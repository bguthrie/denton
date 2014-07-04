(ns denton.core
  (:import  [clojure.lang PersistentArrayMap])
  (:require [clojure.java.jdbc :as sql]
            [clojure.string :as str]
            [honeysql.core :as honey]))

(defprotocol Persistable
  (update-all [this query values]
    "Given a query and a map of values, updates the record and returns the updated values.")
  (insert [this values]
    "Given a map of values, inserts the record and returns the updated values.")
  (delete [this query]
    "Given a query, deletes all records that match it and returns the number affected.")
  (find-one [this query]
    "Given a query, returns the first record matching it.")
  (find-all [this query]
    "Given a query, returns all records matching it.")
  (count-all [this query]
    "Given a query, returns the count of records matching it."))

;; Various helpers.

(defn- merge-query [base-query where-query]
  "Given a base query and a either a map or vector where-query, returns a honeysql map which is the merger of the two."
  (merge base-query (if (vector? where-query) {:where where-query} where-query)))

(defn- col-increment-map [cols]
  "Given a vector of col-names, returns a honeysql map which in conjunction with an update query will result in each column being incremented."
  (->> cols
       (map #(vector
              (keyword %)
              (-> (name %) (str " + 1") (honey/raw))))
       (reduce conj {})))

(defn- wrap-vec [o] (if (vector? o) o [o]))

(defn- sql-time-log [logger-fn table-name operation what-ran expr-fn]
  (let [operation (str/replace (name operation) #"-" " ")
        table-name (str/replace (name table-name) #"_" " ")
        what-ran (str/join ", " what-ran)]
  (try
    (let [start (System/nanoTime) ret (expr-fn) end (- (System/nanoTime) start)]
      (logger-fn
        (format "  %s %s (%.02fms)  %s" operation table-name (float (/ end 1000000)) what-ran))
      ret)
    (catch Exception e
      (logger-fn
        (format "  %s %s (FAILED)  %s" operation table-name what-ran))
      (throw e)))))

;; Stuff you can do now that you have an interface.

(defn find-by-id [persistable id]
  (find-one persistable [:= :id id]))

(defn update-by-id [persistable id values]
  (update-all persistable [:= :id id] values))

(defn delete-by-id [persistable id]
  (delete persistable [:= :id id]))

(defn update-record [persistable record]
  (update-by-id persistable (:id record) record))

(defn delete-record [persistable record]
  (delete-by-id persistable (:id record)))

(defn reload-record [persistable record]
  (find-by-id persistable (:id record)))

(defn exists? [persistable query]
  (> (count-all persistable query) 0))

(defn new-record? [persistable record]
  (or (nil? (:id record)) (not (exists? persistable [:= :id (:id record)]))))

(defn save [persistable record]
  (if (new-record? persistable record)
      (insert persistable record)
      (update-record persistable record)))

(defn increment [persistable query cols]
  (update-all persistable query (col-increment-map cols)))

;; Concrete database-backed implementations and wrappers.

(defrecord SerializePersistable [wrapped ->db <-db]
  Persistable
  (update-all [this query values]
    (when-let [result (update-all wrapped query (->db values))] (<-db result)))
  (insert [this values]
    (<-db (first (wrap-vec (insert wrapped (->db values))))))
  (delete [this query]
    (delete wrapped query))
  (find-one [this query]
    (when-let [result (find-one wrapped query)] (<-db result)))
  (find-all [this query]
    (map <-db (find-all wrapped query)))
  (count-all [this query]
    (count-all wrapped query)))

(defrecord HoneySqlParsePersistable [wrapped table-name]
  Persistable
  (update-all [this query values]
    (let [sql-vec (honey/format (merge-query {:update table-name :set values} query))]
      (update-all wrapped (first sql-vec) (rest sql-vec))))
  (insert [this values]
    (insert wrapped (honey/format {:insert-into table-name :values (wrap-vec values)})))
  (delete [this query]
    (delete wrapped (honey/format (merge-query {:delete-from table-name} query))))
  (find-one [this query]
    (find-one wrapped (honey/format (merge-query {:select [:*] :from [table-name] :limit 1} query))))
  (find-all [this query]
    (find-all wrapped (honey/format (merge-query {:select [:*] :from [table-name]} query))))
  (count-all [this query]
    (count-all wrapped (honey/format (merge-query {:select [:%count.*] :from [table-name]} query)))))

(defrecord LogSqlPersistable [wrapped logger-fn table-name]
  Persistable
  (update-all [this query values]
    (sql-time-log logger-fn table-name :update [query values] #(update-all wrapped query values)))
  (insert [this values]
    (sql-time-log logger-fn table-name :insert values #(insert wrapped values)))
  (delete [this query]
    (sql-time-log logger-fn table-name :delete query #(delete wrapped query)))
  (find-one [this query]
    (sql-time-log logger-fn table-name :find-one query #(find-one wrapped query)))
  (find-all [this query]
    (sql-time-log logger-fn table-name :find-all query #(find-all wrapped query)))
  (count-all [this query]
    (sql-time-log logger-fn table-name :count-all query #(count-all wrapped query))))

(defrecord DbPersistable [conn-fn]
  Persistable
  (update-all [this query values]
    (sql/db-do-prepared-return-keys (conn-fn) query values))
  (insert [this values]
    (sql/db-do-prepared-return-keys (conn-fn) (first values) (rest values)))
  (delete [this query]
    (sql/execute! (conn-fn) query))
  (find-one [this query]
    (first (sql/query (conn-fn) query)))
  (find-all [this query]
    (sql/query (conn-fn) query))
  (count-all [this query]
    (:count (first (sql/query (conn-fn) query)))))

(defrecord LifecyclePersistable [wrapped lifecycle-spec]
  Persistable
  (update-all [this query values]
    (let [ret (update-all wrapped query values)]
      (when-let [after-update (:after-update lifecycle-spec)] (when ret (after-update ret)))
      ret))
  (insert [this values]
    (let [ret (insert wrapped values)]
      (when-let [after-insert (:after-insert lifecycle-spec)] (after-insert ret))
      ret))
  (delete [this query]
    (let [ret (delete wrapped query)]
      (when-let [after-delete (:after-delete lifecycle-spec)] (after-delete ret))
      ret))
  (find-one [this query]
    (find-one wrapped query))
  (find-all [this query]
    (find-all wrapped query))
  (count-all [this query]
    (count-all wrapped query)))

(def wrap-with-serialize ->SerializePersistable)
(def wrap-with-honey     ->HoneySqlParsePersistable)
(def wrap-with-logging   ->LogSqlPersistable)
(def wrap-with-lifecycle ->LifecyclePersistable)
(def sql-db              ->DbPersistable)
