(ns denton.core-expectations
  (:require [midje.sweet :refer :all]
            [denton.core :refer :all]))

(defn stub-persistable [& stubs]
  (let [stubs (or (first stubs) {})]
    (reify Persistable
      (update-all [this query values] (or (:update stubs) values))
      (insert     [this values]    (or (:insert stubs) values))
      (delete     [this query]     (or (:delete stubs) 0))
      (find-one   [this query]     (or (:find-one stubs) {}))
      (find-all   [this query]     (or (:find-all stubs) []))
      (count-all  [this query]     (or (:count stubs) 0))
      )))

(facts "wrap-with-serialize"
  (fact "serializes incoming objects using the given fn"
    (let [store (wrap-with-serialize (stub-persistable) #(str "serialized " %) identity)]
      (update-all store "query" "values") => "serialized values"
      (insert store "values")         => "serialized values"
      ))
  (fact "serializes outgoing objects using the given fn"
    (let [stubs (stub-persistable {:find-one "object" :find-all ["object"]})
          store (wrap-with-serialize stubs identity #(str "deserialized " %))]
      (update-all store "query" "values") => "deserialized values"
      (insert store "values")         => "deserialized values"
      (find-one store {})             => "deserialized object"
      (find-all store {})             => ["deserialized object"]
      )))

(facts "wrap-with-lifecycle"
  (let [out (atom nil) side (fn [val] (reset! out (str "Après moi, " val)))]
    (with-state-changes [(before :facts (reset! out nil))]
      (fact "executes the side effect after update"
        (update-all (wrap-with-lifecycle (stub-persistable {:update "le déluge"}) {:after-update side}) nil nil)
        => "le déluge"
        (deref out)
        => "Après moi, le déluge")
      (fact "does not execute if the return value of update is non-truthy"
        (update-all (wrap-with-lifecycle (stub-persistable {:update nil}) {:after-update side}) nil nil)
        => nil
        (deref out)
        => nil)
      (fact "executes the side effect after insert"
        (insert (wrap-with-lifecycle (stub-persistable {:insert "le dessert"}) {:after-insert side}) nil)
        => "le dessert"
        (deref out)
        => "Après moi, le dessert")
      (fact "executes the side effect after delete"
        (delete (wrap-with-lifecycle (stub-persistable {:delete "le chaton"}) {:after-delete side}) nil)
        => "le chaton"
        (deref out)
        => "Après moi, le chaton"))))

(defn honey-stub-persistable []
  (reify Persistable
    (update-all [this query values] [query values])
    (insert     [this values]       nil)
    (delete     [this query]        query)
    (find-one   [this query]        query)
    (find-all   [this query]        query)
    (count-all  [this query]        query)))

(facts "wrap-with-honey"
  (fact "parses incoming queries with HoneySQL"
    (let [store (wrap-with-honey (honey-stub-persistable) :widgets)]
      (update-all store [:= :id 1] {:foo "bar"})
      => ["UPDATE widgets SET foo = ? WHERE id = 1" '("bar")]
      (delete store [:= :id 1])
      => ["DELETE FROM widgets WHERE id = 1"]
      (find-one store [:= :id 1])
      => ["SELECT * FROM widgets WHERE id = 1 LIMIT 1"]
      (find-one store {:where [:= :id 1] :join [:sales [:= :sales.widget_id :widgets.id]]})
      => ["SELECT * FROM widgets INNER JOIN sales ON sales.widget_id = widgets.id WHERE id = 1 LIMIT 1"]
      (find-all store [:= :color "red"])
      => ["SELECT * FROM widgets WHERE color = ?" "red"]
      (find-all store {:where [:= :color "red"] :from [:better_widgets]})
      => ["SELECT * FROM better_widgets WHERE color = ?" "red"]
      (count-all store {})
      => ["SELECT count(*) FROM widgets"]
      (count-all store [:not [:= :shape "gear"]])
      => ["SELECT count(*) FROM widgets WHERE NOT shape = ?" "gear"]
      )))
