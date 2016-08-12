(ns uncaught-ex.core
  (:require [manifold.executor :as mexecutor])
  (:import java.util.concurrent.Executors
           java.util.concurrent.Executor
           java.util.concurrent.ExecutorService
           java.util.concurrent.ThreadFactory
           java.util.concurrent.TimeUnit))

(def io-name "io-thread")
(def io-group (ThreadGroup. "io"))

(defn exception-handler
  [where]
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (println "Uncaught exception in" where "and thread" (.getName thread) ":" (.getMessage ex)))))

(Thread/setDefaultUncaughtExceptionHandler
 (exception-handler ".setDefaultUncaughtExceptionHandler"))

(defn start-io-executor!
  "Start the io-executor and return the following map:

  :options      The used options
  :thread-count An atom containing the current thread count
  :executor-svc The ExecutorService
  :stats        The atom containing the latest stats"
  []
  (let [max-threads 256
        thread-count (atom 0)
        stats (atom {})
        thread-factory (reify ThreadFactory
                         (newThread [_ runnable]
                           (doto (Thread. io-group
                                          runnable
                                          ;; unchecked-inc will not wrap, we'll produce bad names
                                          ;; but at least no Exception
                                          (str io-name "-" (swap! thread-count unchecked-inc)))
                             (.setUncaughtExceptionHandler (exception-handler ".setUncaughtExceptionHandler")))))]
    {:thread-count thread-count
     :manifold-executor (mexecutor/utilization-executor 0.9 max-threads {:thread-factory thread-factory})
     :cached-executor (Executors/newFixedThreadPool 256 thread-factory)}))

(defn io-exec!
  "Execute f on an IO thread."
  [executor-svc runnable]
  (.execute executor-svc runnable))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "\nTwo exceptions should be logged but the one in the manifold executor gets eaten\n")
  (let [{:keys [manifold-executor cached-executor]} (start-io-executor!)]
    (io-exec! manifold-executor (reify Runnable
                                  (run [_]
                                    ;; try to add try/catch around here

                                    ;; Compile Error - gets eaten
                                    (merge {} (range 0 1))
                                    (println "in manifold executor thread" (.getName (Thread/currentThread))))))
    (io-exec! cached-executor (reify Runnable
                                (run [_]
                                  ;; try to add try/catch around here

                                  ;; Compile Error - gets printed
                                  (merge {} (range 0 1))
                                  (println "in cached executor thread" (.getName (Thread/currentThread))))))
    (.shutdownNow manifold-executor)
    (.shutdownNow cached-executor)))
