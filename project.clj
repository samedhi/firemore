(defproject org.clojars.samedhi/firemore "0.2.0"
  :description "A opinionated binding between clojurescript and Firebase"
  :url "https://firemore.org"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :deploy-repositories {"clojars" {:url "https://clojars.org/repo"
                                   :username :env/clojars_username
                                   :password :env/clojars_password
                                   :sign-releases false}}
  :dependencies [[cljsjs/firebase "5.7.3-1"]
                 [compojure "1.6.1"]
                 [com.bhauman/cljs-test-display "0.1.1"]
                 [cryogen-markdown "0.1.11"]
                 [cryogen-core "0.2.1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/test.check "0.10.0-alpha3" :scope "test"]
                 [ring/ring-devel "1.7.1"]
                 [ring-server "0.5.0"]
                 [figwheel-sidecar "0.5.18"]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-codox "0.10.7"]
            [lein-doo "0.1.10"]
            [lein-figwheel "0.5.13"]
            [lein-ring "0.12.5"]]
  :source-paths ["src"]
  :clean-targets ^{:protect false} ["resource/public/js"]
  :codox {:language :clojurescript}
  :cljsbuild {:builds
              [{:id "dev"
                :figwheel true
                :source-paths ["src"]
                :compiler {:main firemore.core
                           :optimizations :none
                           :output-dir "resources/public/js/out-dev"
                           :asset-path "js/out-dev"
                           :output-to "resources/public/js/app.js"}}
               {:id "test"
                :source-paths ["src" "test"]
                :compiler {:main firemore.test-runner
                           :optimizations :whitespace
                           :output-dir "resources/public/js/out-test"
                           :output-to "resources/public/js/test.js"
                           :pretty-print true}}
               {:id "prod"
                :source-paths ["src"]
                :compiler {:main firemore.core
                           :optimizations :advanced
                           :output-dir "resources/public/js/out-dev-prod"
                           :asset-path "js/out-dev-prod"
                           :output-to "resources/public/js/app.js"}}]}
  :main cryogen.core
  :profiles {:dev {:dependencies [[cider/piggieback "0.4.1"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :ring {:init cryogen.server/init
         :handler cryogen.server/handler})
