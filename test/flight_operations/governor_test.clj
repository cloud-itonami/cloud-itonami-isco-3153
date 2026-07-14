(ns flight-operations.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [flight-operations.store :as store]
            [flight-operations.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-aircraft! st {:aircraft-id "N1234" :type "Cessna 172" :registration "N-1234"})
    (store/register-crew! st {:crew-id "crew-1" :license "Commercial Pilot" :type "fixed-wing"})
    st))

(deftest ok-on-clean-draft-flight-plan
  (let [st (fresh-store)
        proposal {:op :draft-flight-plan :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-on-log-preflight-checklist
  (let [st (fresh-store)
        proposal {:op :log-preflight-checklist :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-aircraft
  (let [st (fresh-store)
        proposal {:op :draft-flight-plan :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:aircraft-id "N9999" :crew-id "crew-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-aircraft (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-crew
  (let [st (fresh-store)
        proposal {:op :draft-flight-plan :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:aircraft-id "N1234" :crew-id "no-such-crew"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-crew (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :draft-flight-plan :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-flight-control-attempt
  (let [st (fresh-store)
        proposal {:op :flight-control :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :forbidden-scope (:rule %)) (:violations v)))))

(deftest hard-on-go-no-go-decision-attempt
  (let [st (fresh-store)
        proposal {:op :go-no-go-decision :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :forbidden-scope (:rule %)) (:violations v)))))

(deftest hard-on-airworthiness-determination-attempt
  (let [st (fresh-store)
        proposal {:op :airworthiness-determination :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :forbidden-scope (:rule %)) (:violations v)))))

(deftest escalates-on-mechanical-concern
  (let [st (fresh-store)
        proposal {:op :flag-mechanical-concern :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :draft-flight-plan :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:aircraft-id "N1234" :crew-id "crew-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-operations-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-operation! st {:aircraft-id "N1234" :crew-id "crew-1" :op :draft-flight-plan})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/operations-of st "N1234"))))
    (is (= 1 (count (store/ledger st))))))
