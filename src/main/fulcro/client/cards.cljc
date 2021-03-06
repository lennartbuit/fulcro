(ns fulcro.client.cards
  #?(:cljs (:require-macros fulcro.client.cards))           ; this enables implicit macro loading
  (:require
    fulcro.client
    [fulcro.client.primitives :as prim]
    fulcro.util))

; At the time of this writing, devcards is not server-rendering compatible, and dom-node is a cljs-only thing.
#?(:clj (in-ns 'devcards.util.utils))
#?(:clj (clojure.core/declare devcards-active?))
#?(:clj (in-ns 'devcards.core))
#?(:clj (clojure.core/declare parse-card-args))
#?(:clj (clojure.core/declare card))
#?(:clj (in-ns 'fulcro.client.cards))

#?(:clj
   (defmacro fulcro-application
     "Embed a Fulcro client application in a devcard. The `args` can be any args you'd
     normally pass to `new-fulcro-client` except for `:initial-state` (which is taken from
     InitialAppState or the card's data). The card's data (which must be a normalized db) will override InitialAppState if it is *not* empty.

     Your application will be placed in an atom at the symbol you specify.

     (defcard my-card
        (fulcro-application my-app-symbol UIRoot))
     "
     [app-sym root-ui & args]
     `(devcards.core/dom-node
        (fn [state-atom# node#]
          (defonce ~app-sym (atom (fulcro.client/new-fulcro-client :initial-state state-atom# ~@args)))
          (if (-> ~app-sym deref :mounted? not)
            (let [use-components-initial-state?# (-> state-atom# deref empty?)]
              (if (and use-components-initial-state?#
                    (fulcro.client.primitives/has-initial-app-state? ~root-ui))
                (reset! state-atom# (fulcro.client.primitives/tree->db ~root-ui (fulcro.client.primitives/get-initial-state ~root-ui nil) true))
                state-atom#)
              (reset! ~app-sym (fulcro.client/mount (deref ~app-sym) ~root-ui node#)))
            (fulcro.client/mount (deref ~app-sym) ~root-ui node#))
          ; Dev cards rendering timing issues...this makes sure the app renders after plugging in the app state, and
          ; triggers a swap on the atom so devcards will show that updated state.
          (js/setTimeout (fn []
                           (swap! state-atom# identity)
                           (fulcro.client.primitives/force-root-render! (-> ~app-sym deref :reconciler))) 100)))))

#?(:clj
   (defmacro defcard-fulcro
     "Create a devcard with a full-blown Fulcro application. The arguments are identical to the devcard's
     defcard, and fulcro options can simply be added to that map under the :fulcro key as a map.

     Initial state is handled as folows:

     1. The card's atom is always used to hold the state
     2. If you supply normal devcard state that is *not* empty, then it will be the state of the application,
     **even if** you use InitialAppState
     3. If the card's state atom starts empty, then InitialAppState will be used on card start

     Note that hot code reload works properly: that is to say that the application within the card will
     not reinitialize on hot code reload. If you want to update initial state or run the started-callback, then
     you must reload the page.

     NOTE: The fulcro application will be placed in an atom on a symbol that is named after the card: `cardname-fulcro-app`

     Examples:

     (defcard-fulcro my-card RootUI)

     ; with markdown doc
     (defcard-fulcro my-next-card ; app is in atom at my-next-card-fulcro-app
        \"# Markdown!\"
        RootUI)

     (defcard-fulcro my-other-card ; app is in atom at my-other-card-fulcro-app
        RootUI
        {} ; required, as initial atom, but empty, so InitialAppState used if present
        {:inspect-data true  ;devcard options
         :fulcro {:started-callback (fn [] (js/console.log \"Hello\"))}}) ; fulcro options

     (defcard-fulcro state-card ; app is in atom at state-card-fulcro-app
        RootUI
        {:a 1} ; FORCE initial state. Ignores InitialAppState
        {:inspect-data true})

     See Bruce Hauman's devcards for more information.
     "
     [& exprs]
     (require 'devcards.util.utils)
     (require 'devcards.core)
     (when (devcards.util.utils/devcards-active?)
       (let [[vname docu root-component initial-data options] (devcards.core/parse-card-args exprs 'fulcro-root-card)
             app-sym        (symbol (str (name vname) "-fulcro-app"))
             fulcro-kvpairs (seq (:fulcro options))
             fulcro-options (reduce concat fulcro-kvpairs)]
         (devcards.core/card vname docu `(fulcro-application ~app-sym ~root-component ~@fulcro-options) initial-data options)))))

#?(:cljs
   (defn make-root
     "Make a root for use in a devcard. This allows you to embed a component with an ident into a devcard without having
     to manually create a placeholder root component. The params are passed to `get-initial-state` of the component
     class."
     [component-class params]
     (prim/ui
       static prim/InitialAppState
       (initial-state [t p] {:ROOT (prim/get-initial-state component-class params)})
       static prim/IQuery
       (query [this] [{:ROOT (prim/get-query component-class)}])
       Object
       (render [this]
         (let [f (prim/factory component-class)
               p (get (prim/props this) :ROOT)]
           (f p))))))
