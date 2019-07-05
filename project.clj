(defproject cryogen "0.1.0"
  :description "Simple static site generator"
  :url "https://github.com/lacarmen/cryogen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[compojure "1.6.1"]
                 [cryogen-markdown "0.1.11"]
                 [cryogen-core "0.2.1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/test.check "0.10.0-alpha3" :scope "test"]
                 [ring/ring-devel "1.7.1"]
                 [ring-server "0.5.0"]
                 [figwheel-sidecar "0.5.18"]]
  :plugins [[lein-cljsbuild "1.1.6"]
            [lein-doo "0.1.10"]
            [lein-figwheel "0.5.13"]
            [lein-ring "0.12.5"]]
  :source-paths ["src"]
  :clean-targets ^{:protect false} ["resource/public/js"]
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
                           :output-to "resources/public/js/test.js"}}]}
  :main cryogen.core
  :profiles {:dev {:dependencies [[cider/piggieback "0.4.1"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :ring {:init cryogen.server/init
         :handler cryogen.server/handler})
