# denton

Denton is simple persistence middleware for Clojure. It supplies a basic protocol and then encourages you to
structure related concerns like logging, SQL parsing, object mapping, and lifecycle events as a series of wrappers
applied to the same core data structure, much like Ring does for HTTP requests.

Denton is designed to be lightweight but has strong opinions about logging (essential), raw SQL (avoid), and macros
with side effects (avoid at all costs). It does not provide any infrastructure for managing "models", but does include
several helpful faculties that `clojure.jdbc` leaves out.

## Usage

```
(require '[denton.core :refer :all])

(def users
  (-> (sql-db env/database-url)
      (wrap-with-honey "users")
      (wrap-with-logging println "users")
      (wrap-with-serialize case/->snake_case_keyword case/->camelCaseKeyword)
      (wrap-with-lifecycle {:after-delete #(chatbot/notify "User " (:id %) " deleted")})
      ))

(find-all users [:= :id 1])                  #=> [{:id 1 ...}]
(find-by-id users 1)                         #=> {:id 1 ...}
(save users {:name "Guybrush Threepwood"})   #=> {:id 3 :name "Guybrush Threepwood"}
(save users {:id 1 :name "Herman Toothrot"}) #=> {:id 3 :name "Herman Toothrot"}
```

## Name

What's in a name? [The best-ever death metal band out of Denton never settled on a name.](http://www.themountaingoats.net/lyrics/ahwtx_lyr.html)

## License

Copyright © 2014 Brian Guthrie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
