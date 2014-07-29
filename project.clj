(defproject denton "0.1.0-SNAPSHOT"
  :description "Simple persistence middleware for Clojure."
  :url "https://github.com/bguthrie/denton"
  :license {
    :name "Eclipse Public License"
    :url "http://www.eclipse.org/legal/epl-v10.html"
  }
  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [honeysql "0.4.3"]
    [org.clojure/java.jdbc "0.3.3"]
    [savagematt/bowen "1.0"]
    ]
  :profiles {
    :dev {
      :dependencies [
        [midje "1.6.2"]
        [org.apache.derby/derby "10.10.2.0"]
        [com.h2database/h2 "1.4.180"]
      ]
      :plugins [
        [lein-midje "3.1.3"]
      ]
  }})
