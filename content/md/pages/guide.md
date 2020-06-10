{:title "Firemore Guide"
 :layout :page
 :klipse true
 :tags  ["documentation" "firemore" "firestore"]
 :navbar? true
 :page-index 0
 :home? true}

1. [What is this?](#what_is_this?)
1. [Getting Started](#getting_started)
1. [Usage](#usage)
    1. [References](#references)
    1. [Reading & Writing](#reading_and_writing)
    1. [Transactions](#transactions)
    1. [Batching](#batching)
    1. [Queries](#queries)
    1. [Using Local State Atom](#using_local_state_atom)
    1. [Authentication](#authentication)
1. [API](#api)
1. [Contributing](#contributing)
1. [Credits](#credits)
1. [License](#license)

# What is this?

Firemore is a [clojurescript](https://clojurescript.org/) library for interacting with [Google Firestore](https://cloud.google.com/firestore). Main features include.

1. Automatic conversions between clojure maps and Firestore [Documents](https://firebase.google.com/docs/firestore/data-model#documents).
1. A [channel](https://github.com/clojure/core.async) based API for reading, writing, and observing Firestore Documents.
1. A succinct syntax for doing [queries](https://firebase.google.com/docs/firestore/query-data/queries) upon Firestore Documents.
1. A binding between the Firestore Database and a clojurescript atom (great for [om](https://github.com/omcljs/om) / [re-frame](https://github.com/Day8/re-frame) / [reagent](https://reagent-project.github.io/)).
1. A drop in [Authentication Solution](https://firebase.google.com/docs/auth/web/firebaseui) for managing your users.

# Getting Started

Add the following to your dependencies in `project.clj` ([lein](https://github.com/technomancy/leiningen)) or `build.boot` ([boot](https://github.com/boot-clj/boot)) to use firemore in a existing project.

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.samedhi/firemore.svg)](https://clojars.org/org.clojars.samedhi/firemore)

# Usage

The following is a walkthrough of the main features of Firemore.

## Preamble
Let us define a new namespace and import [core.async](https://github.com/clojure/core.async). `core.async` is a library for writing "synchronous looking" code for asynchronous processes. Firemore code is fundamentally asynchronous as it is constantly syncing the state of documents between a client and a server. 
```language-klipse
(ns firemore.readme
  (:require
   [cljs.core.async :as async])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))
```

_You can completely ignore this next definition!_ The following code is beyond the scope of this introduction. This creates a function that allows for logging "within" the current frame of execution. Again, this is not necessary at all to proceed. Just squint and move on, let's get to the fun stuff!

```language-klipse
(require '[cljs.pprint :as pprint])

(import '[goog.string StringBuffer])

(defn ->output-fx []
  (let [captured-container js/klipse-container
        a (atom [])]
    (fn [k v]
      (let [sb (StringBuffer.)]
        (binding [*out* (StringBufferWriter. sb)]
          (pprint/pprint v *out*)
          (swap! a conj [k (str sb)])
          (set!
            (.-innerHTML captured-container)
            (str "\nOUTPUT:\n\n" 
              (clojure.string/join
                "\n"
                (for [[k s] @a] (str "[" k "] ->\n" s))))))))))

(let [output (->output-fx)]
  (output "friend1" "I like you.")
  (output "friend2" {:how "are" :you 2 "day" "?"}))

:done
```

## References

Read the documentation on [documents, collections, and references](https://firebase.google.com/docs/firestore/data-model) (just the linked page). Go ahead. I'll wait.

As you read, a Firestore reference is a javascript object with a large number of functions attached to it. A Firemore reference is a vector of keywords and/or strings with length at least 1.

So, the following document reference in Firestore:

```javascript
db.collection('users').doc('alovelace');
```

Becomes this in Firemore:

```clj
[:users "alovelace"]
```

Keywords (the symbols with the `:` prefixed on them) appear at first, third, fifth, ... position. Strings appear at second, fourth, sixth, ... position. The keywords specify the name of the collection. The strings specify the document within a collection.

A reference with even length refers to a document, while a reference with an odd length refers to a collection. So `[:users]` is a reference to the `users` collection. While `[:users "alovelace"]` is a reference to a document *within* the `users` collection.

## Reading and Writing

Ok, let's get things rolling. The firemore library is going to allow us to read, write, and query documents in the Firestore database. So let's import it.

<pre>
<code class="language-klipse" data-external-libs="http://localhost:8000/src">
(require '[firemore.core :as firemore])
</code>
</pre>

I was thinking about [Star Wars](https://www.starwars.com/) this morning, and I couldn't remember. Does Luke Skywalker have Force powers? Let's check!

*Note: The following data exist at the `[:characters "luke"]` reference*
```clj
{:first-name  "Luke"
 :last-name   "Skywalker"
 :force-user? true
 :hair-color  "blond"
 :occupation  "farmboy"}
```

```language-klipse
(go
  (let [->output (->output-fx)
        luke-reference [:characters "luke"]
        luke (async/<! (firemore/get luke-reference))]
        
    (->output "luke" luke)
    
    (->output "force user?" (:force-user? luke))))
    
:done
```

That's right, he does have Force powers! Couldn't remember.

A firemore document is a regular [clojure map](https://clojure.org/reference/data_structures#Maps). Firemore attempts to provide useful defaults for converting the JSON like Firestore document to a clojure like Firemore document. 

```no-highlight
Usage:
(get reference)

Returns a channel. If a document exist at reference, it will be put! upon the
channel. If no document exist at reference, then :firemore/no-document will be
put! on the channel. The channel will then be closed.

Note:
put! ->  clojure.core.async/put!
```

But what if I want to see Luke change over time? What if I want to observe Luke's Heroic Journey? Rather than getting Luke once, let's watch him through time.

The first value pulled from `luke-chan` is the current value at `my-luke-reference`. Every `write!`, `merge!` or `delete!` at `my-luke-reference` will result in the updated document being place on `luke-chan`.

Note that the `my-luke-reference` begins with `[:users user-id]`. This is because I have set up [security rules](https://firebase.google.com/docs/firestore/security/get-started) so that you and only you may read and write to the location under `users/<user-id>` in the Firestore database. This is necessary because this database is being used by everyone currently reading this documentation. The security rule allows me to carve out a little place in the database for you to play without conflicting with others.

In case you haven't seen it before `(async/<! ...)` allows you to await the result of the `...` expression. This is only a half truth, and the details are more nuanced (and interesting!). Note that `(async/<! ...)` can only be used within a `(go ...)` or `(go-loop ...)` expression. Let's squint again and move on, but later on you should definitely take some time to read about [core.async](https://github.com/clojure/core.async).

```language-klipse
(go
  (let [->output (->output-fx)
        user-id (async/<! (firemore/uid))
        luke-reference [:characters "luke"]
        luke (async/<! (firemore/get luke-reference))
        my-luke-reference [:users user-id :characters "luke"]]
        
    ;; Copy Luke so we have something to watch and modify; add an occupation
    (firemore/write! my-luke-reference (assoc luke :occupation "farmboy"))
  
    (let [luke-chan (firemore/watch my-luke-reference)]
      ;; Luke's journey starts off as a farmboy
      (->output "Teenage Luke" (async/<! luke-chan))

      ;; Changing Luke's adult occupation...
      (async/<! (firemore/write! my-luke-reference (assoc luke :occupation "jedi")))
      (->output "Adult Luke" (async/<! luke-chan))

      ;; Changing to Lukes final occupation after episode 7
      (async/<! (firemore/write! my-luke-reference (assoc luke :occupation "One with the Force")))
      (->output "Episode 8 Luke" (async/<! luke-chan))

      ;; Remove luke from the Firestore database
      (async/<! (firemore/delete! my-luke-reference))
      (->output "Deleted Luke" (async/<! luke-chan))

      ;; Remember to close the channel when you are done with it!
      (async/close! luke-chan))))
      
:done
```

```no-highlight
Usage:
(watch reference)

Returns a channel. If a document exist at reference, it will be put! upon
the channel. If no document exist at reference, then :firemore/no-document will
be put! on the channel. As the document at reference is updated through
time, the channel will put! the newest value of the document (if it exist)
or :firemore/no-document (if it does not) upon the channel.

Important: close! the channel to clean up the state machine feeding this
channel. Failure to close the channel will result in a memory leak.

Note:
put! ->  clojure.core.async/put!
close! ->  clojure.core.async/close!
```

```no-highlight
Usage:
(write! reference m)

Returns a channel. Overwrites the document at reference with m.  Iff an error
occurs when writing m to Firestore, then the error will be put! upon the
channel. The channel will then be closed.

Note:
put! ->  clojure.core.async/put!
```

```no-highlight
Usage:
(merge! reference m)

Returns a channel. Updates (merges in novelty) the document at reference with m.
Iff an error occurs when writing m to Firestore, then the error will be put!
upon the channel. The channel will then be closed.

Note:
put! ->  clojure.core.async/put!
```

```no-highlight
Usage:
(delete! reference)

Returns a channel. Iff an error occurs when deleting reference from Firestore,
then the error will be put! upon the channel. The channel will then be closed.

Note:
put! -> clojure.core.async/put!
```

## Transactions

Firemore also supports [transactions](https://firebase.google.com/docs/firestore/manage-data/transactions). Transactions allow for atomic reads and writes within the Firestore database. Let us read the value at two documents and write them to a third document. We are reading the midichlorian count from Padme & Anakin and writing the midichlorian count back to Luke. Note that we are using the third (optional) argument to all our Firestore read & write functions to specify the transaction `trx`.

```language-klipse
(go
  (let [->output (->output-fx)
        user-id (async/<! (firemore/uid))]
    (->> 
     (fn [trx]
      (go
       (let [{anakin-midichlorians :midichlorian} (async/<! (firemore/get [:characters "anakin"] {:transaction trx})) ;; 27700
             {padme-midichlorians  :midichlorian} (async/<! (firemore/get [:characters "padme"] {:transaction trx})) ;; 4700
             midichlorians-average (/ (+ padme-midichlorians anakin-midichlorians) 2)] ;; 16200
       (firemore/write! [:users user-id :test "luke"] {:midichlorian midichlorians-average} {:transaction trx})
       (str "midichlorians count is " midichlorians-average))))
     firemore/transact!
     async/<!
     (->output "Transaction Result"))))
     
:done
```

## Batching

[Batching](https://firebase.google.com/docs/firestore/manage-data/transactions#batched-writes) writes is supported. Batches allow for atomic (all or nothing) writes, updates, and deletions of Firestore documents.

```language-klipse

(go
     (let [->output (->output-fx)
           user-id    (async/<! (firemore/uid))
           reference  [:users user-id :batch-test]
           [r1 r2 r3] (->> (repeatedly 3 random-uuid)
                           (map str)
                           (map #(conj reference %)))
           btx1       (firemore/create-batch)
           btx2       (firemore/create-batch)]
       (firemore/write! r1 {:value 1} {:batch btx1})
       (firemore/write! r2 {:value 2} {:batch btx1})
       (firemore/write! r3 {:value 3} {:batch btx1})
       (async/<! (firemore/commit-batch! btx1))
       (->output :first-batch-writes
         [(async/<! (firemore/get r1))
          (async/<! (firemore/get r2))    
          (async/<! (firemore/get r3))])
       (firemore/write!  r1 {:value 4} {:batch btx2})
       (firemore/merge!  r2 {:value 5} {:batch btx2})
       (firemore/delete! r3            {:batch btx2})
       (async/<! (firemore/commit-batch! btx2))
       (->output :second-batch-writes
         [(async/<! (firemore/get r1))
          (async/<! (firemore/get r2))    
          (async/<! (firemore/get r3))])))

:done
```


## Queries

*Note: The following data exist at the `[:cities]` reference*
```clj
{"SF"  {:name "San Francisco"
        :state "CA"
        :country "USA"
        :capital false
        :population 860000
        :regions ["west_coast" "norcal"]}
 "LA"  {:name "Los Angeles"
        :state "CA"
        :country "USA"
        :capital false
        :population 3900000
        :regions ["west_coast" "socal"]}
 "DC"  {:name "Washington, D.C."
        :state nil
        :country "USA"
        :capital false
        :population 680000
        :regions ["east_coast"]}
 "TOK" {:name "Tokyo"
        :state nil
        :country "Japan"
        :capital false
        :population 9000000000
        :regions ["kantu" "honshu"]}
 "BJ"  {:name "Beijing"
        :state nil
        :country "China"
        :capital false
        :population 21500000
        :regions ["jingjinji" "hebei"]}}
```

First [read the documentation on queries](https://firebase.google.com/docs/firestore/query-data/queries). Firestore queries are built from a collection reference. Firemore queries are built by adding a query map to the end of the reference vector.

So this in Firestore
```javascript
db.collection("cities").where("state", "==", "CA").where("population", "<", 1000000);
```

Becomes this in Firemore

```clojure
[:cities {:where [[:state "==" "CA"] 
                  [:population "<" 1000000]]}]
```

```language-klipse
(go
  (let [->output (->output-fx)
        cities (firemore/get [:cities {:where [[:state "==" "CA"] 
                                               [:population "<" 1000000]]}])]
    (->output "cities" (async/<! cities))))

:done
```

### Containment

`array-contains` allows you to ask the question "Does this array contain this value?" The following query ask for all the cities that contain `"west_coast"` in their `:regions` array.

```language-klipse
(go
  (let [->output (->output-fx)
        cities (firemore/get [:cities {:where [":regions" "array-contains" "west_coast"]}])]
    (->output "west coast cities" (async/<! cities))))

:done
```

### "Or" (union) Queries

`in` (for non-array fields) and `array-contains-any` (for array fields) both take an array of values to match against a field. For every document in the collection, if any value in the array is found within the field, then the document is returned.


The following query ask for all cities that have a `:country` field of either `"Japan"` or `"USA"`.

```language-klipse
(go
  (let [->output (->output-fx)
        cities (firemore/get [:cities {:where [":country" "in" ["USA" "Japan"]]}])]
    (->output "west coast cities" (async/<! cities))))

:done
```

The following query ask for all of the `:cities` that contain either `"west_coast"` or `"east_coast"` in their regions.

```language-klipse
(go
  (let [->output (->output-fx)
        cities (firemore/get [:cities {:where [":regions" "array-contains-any" ["west_coast" "east_coast"]]}])]
    (->output "west coast cities" (async/<! cities))))

:done
```

### Order & Limit

Queries also support the [orderBy and limit option](https://firebase.google.com/docs/firestore/query-data/order-limit-data).

So this in Firestore
```javascript
citiesRef.where("population", ">", 100000).orderBy("population").orderBy("state", "desc").limit(2)
```

becomes this in firemore
```language-klipse
(go
  (let [->output (->output-fx)
        cities (firemore/get [:cities {:where [[:population "<" 1000000]]
                                       :order [[:population "asc"] [:state "desc"]]
                                       :limit 2}])]
    (->output "two biggest cities" (async/<! cities))))
    
:done
```

### Shorthand

If you have only one `:where` clause predicate it may be specified as a single vector. The following is equivalent to the above.

```clj
[:cities {:where [:population "<" 1000000]
          :order [[:population "asc"] [":state" "desc"]]
          :limit 2}]
```

The `:order` values are expanded into 2 element vectors of `[<property> "asc"]` if they are specified as strings or keywords. So the following is also equivalent to the above.

```clj
[:cities {:where [:population "<" 1000000]
          :order [:population ["state" "desc"]]
          :limit 2}]
```

## Using Local State Atom

Let's say you want a ordered list of the best of the [Three Stooges](https://en.wikipedia.org/wiki/The_Three_Stooges)? You want a per stooge button that lets you vote for each stooge, as well as a ordered list of stooges from highest to lowest vote. The HTML below is printed and then rendered immediately following.

```markdown
### Best Stooge Official Ranking

<span id="ordered-stooges" style="font-size: 2em">Loading...</span>

<table>
 <tbody class="stooge-table">
  <tr>
   <th> Vote for Moe </th>
   <th> <button id="moe-button" style="margin: 0.5em 1em">Vote</button> </th>
   <th> <span id="moe-votes">-1</span> </th>
  </tr>
  <tr>
   <th> Vote for Larry </th>
   <th> <button id="larry-button" style="margin: 0.5em 1em">Vote</button> </th>
   <th> <span id="larry-votes">-1</span> </th>
  </tr>
  <tr>
   <th> Vote for Curly </th>
   <th> <button id="curly-button" style="margin: 0.5em 1em">Vote</button> </th>
   <th> <span id="curly-votes">-1</span> </th>
  </tr>
 </tbody>
</table>
```

### Best Stooge Official Ranking

<span id="ordered-stooges" style="font-size: 2em">Loading...</span>

<table>
 <tbody class="stooge-table">
  <tr>
   <th> Vote for Moe </th>
   <th> <button id="moe-button" style="margin: 0.5em 1em">Vote</button> </th>
   <th> <span id="moe-votes">-1</span> </th>
  </tr>
  <tr>
   <th> Vote for Larry </th>
   <th> <button id="larry-button" style="margin: 0.5em 1em">Vote</button> </th>
   <th> <span id="larry-votes">-1</span> </th>
  </tr>
  <tr>
   <th> Vote for Curly </th>
   <th> <button id="curly-button" style="margin: 0.5em 1em">Vote</button> </th>
   <th> <span id="curly-votes">-1</span> </th>
  </tr>
 </tbody>
</table>

With our current knowledge, we will need at least 4 observers to the Firestore database. One observer for each stooge (3), plus one observer for the list of stooges sorted by the number of votes they have received. We will in addition need to create a state machine that takes the result from each observer and places it within the `<stooge>-votes` element, as well as creating a closure to save the value so that we can send the incremented value to the server upon button clicks. That is a good amount of work.

Or we can just do the following. 

```language-klipse
;; The app that holds our state
(def app (atom {}))

(defn set-by-id! [id v]
  (set! (.-textContent (js/document.getElementById id)) v))

;; writes new value to our 4 separate <span>'s
(defn watcher [_ _ _ n]
  (set-by-id! "moe-votes" (get-in n [:firestore "moe" :votes]))
  (set-by-id! "larry-votes" (get-in n [:firestore "larry" :votes]))
  (set-by-id! "curly-votes" (get-in n [:firestore "curly" :votes]))
  (set-by-id! "ordered-stooges" 
    (clojure.string/join " > " 
      (map #(-> % meta :id) (get-in n [:firestore :ordered-stooges])))))

;; on every change to app, call watcher with the newest value of app
(add-watch app :update-stooge-values watcher)

(go
 (let [user-id (async/<! (firemore/uid))]
  (doseq [[stooge i] (map vector ["moe" "larry" "curly"] (range))]
   ;; Give every stooge a initial vote count
   (async/<! (firemore/write! [:users user-id :stooges stooge] {:votes i}))
   ;; Register a event click handler for every vote button
   (set! (.-onclick (js/document.getElementById (str stooge "-button")))
         (fn [event] 
          (.preventDefault event)
          (let [votes (get-in @app [:firestore stooge :votes] 0)]
           (firemore/write! [:users user-id :stooges stooge] {:votes (inc votes)})))))
           
  ;; bind keys in the app to specific references in firestore
  (firemore/add! app ["moe"]   [:users user-id :stooges "moe"])
  (firemore/add! app ["larry"] [:users user-id :stooges "larry"])
  (firemore/add! app ["curly"] [:users user-id :stooges "curly"])
  (firemore/add! app [:ordered-stooges] [:users user-id :stooges {:order [[":votes" "desc"]]}])))

:done
```

Using `firemore/add!`, we can cause an atom to be updated with the most recent value at a `reference`. `firemore/subtract!` can be used to undo `firemore/add!`. This is very useful for single page apps (SPA) that often rely on having a single atom that drive the application. See [om](https://github.com/omcljs/om) / [re-frame](https://github.com/Day8/re-frame) / [reagent](https://reagent-project.github.io/) for examples of frameworks for building SPAs.

```no-highlight
Usage:
(add atm path reference)

Sync the current value of `reference` at `path` within the `atm`

atm - A clojure atom.
path - a vector location within the `atm` where the Firestore `reference` will be written.
reference - a reference to a location in Firestore.

Note that the the {path reference} will show up under the :firemore key, and the
{path reference-value} will show up under the :firemore key in `atm`.
```

```no-highlight
Usage:
(subtract! atm path)

Remove the `path` from the `atm`
```

## Authentication

Firestore supports a few primitive and one "ready made" solution for authentication. The primitives include the `(logout!)`, `(login-anonymously!)`, and `(user-atom)` functions. The "ready made" solution is the use of [firebaseUI-Auth](https://github.com/firebase/firebaseui-web) as a drop in authentication solution.

### Primitives

```no-highlight
Usage:
(logout!)

Log out any currently logged in user.
```

```no-highlight
Usage:
(login-anonymously!)

Log in a new anonymous user; noop if already logged in.
```

Anonymous does NOT mean unidentified (you have a unique user id in `:uid`). Anonymous does mean that we don't know your `:email`, `:name`, or `:photo`. Anonymous means that if you logout from this account or loose access to this system, there would be no way to log back in as this anonymous user (though you could always login as a new anonymous user).

### FirebaseUI-Auth

[FirebaseUI-Auth](https://github.com/firebase/firebaseui-web) FirebaseUI-Auth provides a drop-in authentication solution that handles the UI flows for signing in users with email/passwords, phone numbers, and Identity Provider's including Google, Facebook, GitHub, Twitter, Apple, Microsoft, Yahoo, OpenID Connect (OIDC) providers and SAML providers.

For illustrating details please consult [the docs](https://github.com/firebase/firebaseui-web); but minimal setup is to add the following two lines to your `index.html` within the `<head>` tag.

```
<script src="https://www.gstatic.com/firebasejs/ui/4.5.1/firebase-ui-auth.js"></script>
<link type="text/css" rel="stylesheet" href="https://www.gstatic.com/firebasejs/ui/4.5.1/firebase-ui-auth.css" />
```

Then enable and instantiate FirebaseUI-Auth by adding the following to the `body` tag in your `index.html`.

```
<script>
   FIREBASE_AUTH_UI_CONFIG = {enabled: true}
</script>
<div id="firebaseui-auth-container"></div>
```

Note that this must be added BEFORE your clojurescript (using firemore) application is loaded; so your body might look something like the following.

```
<body>
  <script>
    FIREBASE_AUTH_UI_CONFIG = {enabled: true}
  </script>
  <div id="firebaseui-auth-container"></div>
  <script src="js/app.js"></script>
</body>
```

Note that there are more options that can be configured for FirebaseUI-Auth; here is the map containing all the config options.

```
{  ;; Do you want to enable the FirebaseUI-Auth?
   :enabled false

   ;; URL that you will redirect to upon signin success
   :signInSuccessUrl "/"

   ;; The options you want enabled as part of this firebase app
   :signInOptions [
                   "anonymous"    ;; Continue as guest
                   "facebook.com" ;; Sign in with Facebook
                   "github.com"   ;; Sign in with GitHub
                   "google.com"   ;; Sign in with Google
                   "twitter.com"  ;; Sign in with Twitter
                   "password"     ;; Sign in with email
                   "phone"        ;; Sign in with phone
                   ]

   ;; Your Terms of Service
   :tosUrl "<your-tos-url>"

   ;; Redirect to your privacy policy
   :privacyPolicyUrl #(js/window.location.assign "<your-privacy-policy-url>")

   ;; querySelector of the DOM element that firebaseUI-auth should be rendered within
   :container-selector "#firebaseui-auth-container"}
```

### Get Current User

Regardless of how you log in, you still need to know about the currently logged in user. `(user-atom)` will return an atom that will be updated to reflect the currently logged in user. More information about the keys and values within this atom can be [found here](https://firebase.google.com/docs/auth).

```no-highlight
Usage
(user-atom)

Return the atom that reflects the state of currently logged in user
```

# Contributing

Pull Request are always welcome and appreciated. If you want to discuss firemore, I am available most readily:
1. On [clojurians.slack.com under #firemore](https://clojurians.slack.com/messages/C073DKH9P/).
1. Through the [issue tracking system](https://github.com/samedhi/firemore/issues).
1. By email at stephen@read-line.com .

# Credits

[Stephen Cagle](https://samedhi.github.io/) is a Senior Software Engineer at [Dividend Finance](https://www.dividendfinance.com/) in San Francisco, CA. He is the original (currently only, but always accepting PRs!) creator/maintainer of firemore.
[@github](https://github.com/samedhi)
[@linkedin](https://www.linkedin.com/in/stephen-cagle-92b895102/)

![Man (Stephen Cagle) holding beer & small dog (Chihuahua)](https://firemore.org/img/stephen_and_nugget_1200.jpg)

# License

MIT License

Copyright (c) 2019 Stephen Cagle
