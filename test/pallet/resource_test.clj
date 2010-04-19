(ns pallet.resource-test
  (:use pallet.resource :reload-all)
  (:require pallet.resource.test-resource
    pallet.compat)
  (:use clojure.test
        pallet.test-utils))

(pallet.compat/require-contrib)

(def test-atom (atom []))

(deftest reset-resources-test
  (with-init-resources {:k :v}
    (reset-resources)
    (is (= {} *required-resources*))))

(deftest in-phase-test
  (in-phase :fred
    (is (= :fred *phase*))))

(deftest after-phase-test
  (is (= :after-fred (after-phase :fred))))

(deftest execute-after-phase-test
  (in-phase :fred
    (execute-after-phase
     (is (= :after-fred *phase*)))))

(deftest add-invocation-test
  (with-init-resources nil
    (is (= {:configure [[:a :b]]}
          (set! *required-resources* (add-invocation *required-resources* [:a :b]))))
    (in-phase :fred
      (is (= {:configure [[:a :b]] :fred [[:c :d]]}
            (set! *required-resources* (add-invocation *required-resources* [:c :d])))))))

(deftest invoke-resource-test
  (reset! test-atom [])
  (with-init-resources nil
    (invoke-resource test-atom identity :a)
    (is (= [:a] @test-atom))
    (is (= {:configure [[identity test-atom]]} *required-resources*))

    (invoke-resource test-atom identity :b)
    (is (= [:a :b] @test-atom))
    (is (= {:configure [[identity test-atom][identity test-atom]]}
          *required-resources*))))

(with-private-vars [pallet.resource [produce-resource-fn]]
  (deftest produce-resource-fn-test
    (reset! test-atom [])
    (swap! test-atom conj :a)
    (let [f (produce-resource-fn [identity test-atom])]
      (is (= [] @test-atom))
      (is (= [:a] (f))))))

(deftest configured-resources-test
  (reset! test-atom [])
  (with-init-resources nil
    (invoke-resource test-atom identity :a)
    (invoke-resource test-atom identity :b)
    (let [fs (configured-resources)]
      (is (not (.contains "lazy" (str fs))))
      (is (= [] @test-atom))
      (reset-resources)
      (is (= [:a :b] ((first (fs :configure))))))))

(defn test-combiner [args]
  (string/join "\n" args))

(deftest defresource-test
  (reset! test-atom [])
  (with-init-resources nil
    (defresource test-resource test-atom identity [arg])
    (test-resource :a)
    (is (= [[:a]] @test-atom))))

(defn- test-component-fn [arg]
  (str arg))

(defcomponent test-component test-component-fn [arg & options])

(deftest defcomponent-test
  (with-init-resources nil
    (is (= ":a\n" (build-resources [] (test-component :a))))))

(deftest resource-phases-test
  (with-init-resources nil
    (let [m (resource-phases (test-component :a))])))

(deftest output-resources-test
  (with-init-resources nil
    (is (= "abc\nd\n"
          (output-resources :a {:a [(fn [] "abc") (fn [] "d")]})))
    (is (= nil
          (output-resources :b {:a [(fn [] "abc") (fn [] "d")]})))))

(deftest produce-phases-test
  (with-init-resources nil
    (is (= "abc\nd\n"
          (produce-phases [:a] "tag" [] {:a [(fn [] "abc") (fn [] "d")]})))
    (is (= ":a\n"
          (produce-phases [(phase (test-component :a))] "tag" [] {})))))

(deftest build-resources-test
  (reset! test-atom [])
  (with-init-resources nil
    (let [s (build-resources []
              (invoke-resource test-atom test-combiner "a")
              (invoke-resource test-atom test-combiner "b"))]
      (is (= [] @test-atom))
      (is (= "a\nb\n" s)))))

(deftest defphases-test
  (with-init-resources nil
    (let [p (defphases
              :pa [(test-component :a)]
              :pb [(test-component :b)])]
      (is (map? p))
      (is (= [:pa :pb] (keys p)))
      (is (= ":a\n" (output-resources :pa p)))
      (is (= ":b\n" (output-resources :pb p))))))

(deftest phase-test
  (with-init-resources nil
    (let [p (phase (test-component :a))]
      (is (vector? p))
      (is (keyword? (first p)))
      (is (map? (second p)))
      (is (= [(first p)] (keys (second p))))
      (is (= ":a\n" (output-resources (first p) (second p)))))))
