(ns flight-operations.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [flight-operations.store :as store]
            [flight-operations.advisor :as advisor]
            [flight-operations.actor :as actor]))

(defn- fresh-store-and-graph []
  (let [st (store/mem-store)]
    (store/register-aircraft! st {:aircraft-id "N1234" :type "Cessna 172" :registration "N-1234"})
    (store/register-crew! st {:crew-id "crew-1" :license "Commercial Pilot" :type "fixed-wing"})
    (let [g (actor/build-graph {:store st :advisor (advisor/mock-advisor)})]
      [st g])))

(deftest run-clean-flight-plan-draft-to-commit
  (let [[st g] (fresh-store-and-graph)
        request {:aircraft-id "N1234" :crew-id "crew-1" :op :draft-flight-plan :stake :low}
        result (actor/run-request! g request {} "thread-1")]
    (is (= :done (:status result)))
    (is (not (:interrupted result)))
    (is (some? (-> result :state :operation)))
    (is (= :draft-flight-plan (-> result :state :operation :op)))))

(deftest run-request-escalates-on-mechanical-concern
  (let [[st g] (fresh-store-and-graph)
        request {:aircraft-id "N1234" :crew-id "crew-1" :op :flag-mechanical-concern :stake :high}
        result (actor/run-request! g request {} "thread-2")]
    (is (= :interrupted (:status result)))
    (is (some? (-> result :state :proposal)))
    (is (= :flag-mechanical-concern (-> result :state :proposal :op)))))

(deftest run-request-holds-on-hard-violation
  (let [[st g] (fresh-store-and-graph)
        request {:aircraft-id "N9999" :crew-id "crew-1" :op :draft-flight-plan :stake :low}
        result (actor/run-request! g request {} "thread-3")]
    (is (= :done (:status result)))
    (is (nil? (-> result :state :operation)))
    (is (some? (-> result :state :verdict)))))

(deftest approve-escalated-request
  (let [[st g] (fresh-store-and-graph)
        req1 {:aircraft-id "N1234" :crew-id "crew-1" :op :flag-mechanical-concern :stake :high}
        result1 (actor/run-request! g req1 {} "thread-4")]
    (is (= :interrupted (:status result1)))
    (let [result2 (actor/approve! g "thread-4")]
      (is (= :done (:status result2)))
      (is (some? (-> result2 :state :operation))))))

(deftest audit-ledger-records-all-dispositions
  (let [[st g] (fresh-store-and-graph)
        req1 {:aircraft-id "N1234" :crew-id "crew-1" :op :draft-flight-plan :stake :low}
        _result1 (actor/run-request! g req1 {} "thread-5")
        ledger (store/ledger st)]
    (is (> (count ledger) 0))
    (is (some #(= :commit (:disposition %)) ledger))))
