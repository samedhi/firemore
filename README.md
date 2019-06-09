# firemore

**Firemore** is a library for writing [clojure](https://clojure.org/)([script](https://clojurescript.org/)) applications using [Google Firestore](https://cloud.google.com/firestore).

Main features include:
1. A direct API to the [Firestore Database](https://firebase.google.com/docs/firestore).
1. Automatic (and customizable) conversions between clojure maps and Firestore "json" documents.
1. A channels based API for getting and observing Firestore documents.
1. A binding between the Firestore Cloud database and a local clojure atom (great for om/re-frame/reagent).
1. Simply update the document in the atom effect the change in the

# Table of Contents
1. [Getting Started](#getting_started)
1. [Usage](#usage)
1. [API](#api)
1. [Contributing](#contributing)
1. [Credits](#credits)
1. [License](#license)

# <a id="getting_started"></a>Getting Started

To use firemore in an existing project, simply add this to your dependencies in project.clj ([lein](https://github.com/technomancy/leiningen)) or build.boot ([boot](https://github.com/boot-clj/boot)).

!!!!! [firemore "x.y.z"] @clojars !!!!!!

# <a id="usage"></a>Usage

;; TODO: Transparently convert arrays to Sets and back. Auto index for containment?

;; TODO: Transparently convert keywords to strings and back? Maybe write some checksum at front of keyword?

;; TODO: Add in metadata whether value you see if realized or not.

The following is a walkthrough of the main features of firemore.

## Authentication
Most apps require some way of authenticating a user.
Firestore includes a fairly robust [Authentication System](https://firebase.google.com/docs/auth).
Use of the built in authentication system will allow you to complete your project
more quickly and securely than rolling your own solutions.

```clojurescript
(def user-atm (get-user-atom))

;; The value within user-atm is currently nil as you are not logged in.
(assert (= nil @user-atm))

;; Let's log you in as the anonymous user
(login-as-anonymous)

;; having done that, let's check what user-atm looks like now
(println "(1) user-atm contains -> " @user-atm)

;; Let's demonstrate logging in and out a few times. Note that your `:uid`
;; changes every time you login again with a new anonymous user.
(login-as-anonymous)
(println "(2) user-atm contains ->" @user-atm)

(login-as-anonymous)
(println "(3) user-atm contains ->" @user-atm)
```

```
Usage:
(get-user-atom)

Returns a atom containing either a user-map or nil. Atom will contain nil when
no user is logged into Firestore. Atom will contain a user-map if a user is
currently logged in. user-map has the following form:

{:uid <application_unique_id>
 :email <user_email_address>
 :name <user_identifier>
 :photo <url_to_a_photo_for_this_user>}

Note: :uid will always be present. :email, :name, :photo may be present depending
on sign-in provider and/or whether you have set their values.
```

```
Usage:
(login-as-anonymous)

Log out any existing user, then log in a new anonymous user.
```

You have been logged in as a anonymous user. Anonymous does NOT mean
unidentified (you have a unique user id in `:uid`). Anonymous does however mean that
we don't know your `:email`, `:name`, or `:photo`. Anonymous means that if you
were to logout from this account or loose access to this system, there would be
no way for you to log back in as this anonymous user (though you could always
login as a new anonymous user).

```clojurescript
;; Of course, you can also logout, let's demonstrate this.

(println "(1) user-atm -> " @user-atm)

(logout)
(println "(2) user-atm ->" @user-atm)
```

```
Usage:
(logout)

Log out the currently logged in user (if any).
```

```clojurescript
;; Most applications will also need to allow users to delete their accounts.
;; This is trivial in Firestore.

(login-as-anonymous)
(println "Check that we have a user ->" @user-atm)

(delete-user)
(println "We have been logged out as our user is deleted ->" @user-atm)
```

```
Usage:
(delete-user)

Deletes the user specified by user-id from Firestore. This removes all sign-in
providers for this user, as well as deleting the data in the user information
map returned by (get-user-atom). Note that this does NOT delete information
relating to the user from the actual Firestore database.
```

## References

Read the documentation on [documents, collections,
and references](https://firebase.google.com/docs/firestore/data-model) (just
the linked page). Go ahead. I'll wait.

As you just read, a Firestore reference is a opaque javascript object with a
bunch of functions attached to it. In firemore a reference is a vector of
keywords or strings with length at least 1.

So, the following document reference in firestore
```
db.collection('users').doc('alovelace');
```
Becomes this in firemore:
```
["users" "alovelace"] ;; OR
[:users "alovelace"]  ;; OR
["users" :alovelace]  ;; OR
[:users :alovelace]
```

Note that keywords and strings are interchangeable. I prefer to use keywords
in collection (odd) positions and strings in (even) positions, but it is up to
you.

Note that a vector of even length must be a reference to a
document, while a vector of odd length must be a reference to a collection. So
`[:users]` is a reference to the `users` collection. While
`[:users "alovelace"]` is a reference to a document *within* the `users`
collection.

## Read from Firestore

The map to your right shows the data currently in your section of the firestore
database. I have taken the liberty of setting you up with some starting data so
that we can practice reading it using firemore.

```clojurescript
(def user-atm (get-user-atom))

(def user-id (:uid @user-atm))

(def luke-reference [:users user-id :characters "luke"])
```

Note that the reference (vector) begins with `[:users user-id]`. This is because
I have set up [security rules](https://firebase.google.com/docs/firestore/security/get-started)
so you and only you may read and write to a location
under `users/<user-id>` in the Firestore database. This is necessary because
this database is being used by everyone currently looking at this document. The
security rule allows me to carve out a little place in the database for you to
work within without conflicting with others.

Is Luke Skywalker a force user? I can never remember? Let's check!
```clojurescript
(def luke (<! (get luke-reference)))

(println "luke ->" luke)

(:force-user? luke)
```

That's right, he does have force powers! Couldn't remember.

A firemore document is a regular [clojure map](https://clojure.org/reference/data_structures#Maps).
There is a fair amount of conversion to allow for conversion between a Firestore
document and a firemore document (see [Clojure Interop](#clojure_interop) for details).

```
Usage:
(get reference)

Returns a channel. If a document exist at reference, it will be put! upon the
channel. If no document exist at reference, then :firemore/no-document will be
put! on the channel. The channel will then be closed.

Note:
put! ->  clojure.core.async/put!
```

But what if I want to watch Luke change over time? I could periodically check for
updates; this is error prone, verbose, and inefficient. Rather than getting
Luke once, let's watch him from now on.

```clojurescript
(def luke-chan (watch luke-reference))

(def luke-atm (atom nil)

(go-loop [i 2]
  (when-let [luke (<! luke-chan)]
    (println i "luke ->" luke)
    (reset! luke-atm luke)
    (recur (inc i))))

TODO: Stupid, make this thread safe

(async/go
  (let [luke (<! c)]

    (println "Adding in Luke's initial occupation...")
    (<! (write! luke-reference (assoc luke :occupation "farmboy")))
    (<! (timeout 1000))

    (println "Changing Luke's adult occupation...")
    (<! (write! luke-reference (assoc luke :occupation "jedi")))
    (<! (timeout 1000))

    (println "Changing to Lukes final occupation after episode 7")
    (<! (write! luke-reference (assoc luke :occupation "One with the Force")))
    (<! (timeout 1000))

    ;; Remember to close the channel when you are done with it!
    ;; (Stop watching Luke)
    (close! luke-chan)))
```

I put small pauses of 1 second between each change to Luke so that we can
observe the go-loop println firing. Notice that Luke 1 is the initial value of
of luke-reference in the database. Luke 2, 3, and 4 reflect the three separate
changes made to the Luke document.

```
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

## Write to Firestore

```
Usage:
(write! reference m)

Returns a channel. Overwrites the document at reference with m.  Iff an error
occurs when writing m to Firestore, then the error will be put! upon the
channel. The channel will then be closed.

Note:
put! ->  clojure.core.async/put!
```

```
Usage:
(merge! reference m)

Returns a channel. Updates (merges in novelty) the document at reference with m.
Iff an error occurs when writing m to Firestore, then the error will be put!
upon the channel. The channel will then be closed.

Note:
put! ->  clojure.core.async/put!
```

TODO: Delete

## <a id="clojure_interop"></a> Clojure Interop

```
Usage:
(fire->clj js-object)
(fire->clj js-object opts)

Returns the clojure form of the js-object document from Firestore. opts
allows you to modify the conversion.
```

```
(clj->fire document)
(clj->fire document options)

Returns a javascript object that can be passed to Firestore for writes. opts
allows you to modify the conversion.
```

## Build Local State Atom

:firemore/path to indicate that this is a path that should hydrate at this Locations
can shortcut by just providing a path (a vector).

What about the notion of unifying the local path and the firestore path with a keyword? No keyword
or unfound keyword means just use the local-path for firestore... Maybe dumb actually.

documents paths just become maps with the additional key of :firemore/path in their metadata

collections become maps of maps with the key being the id of the item. The collection itself
will have the collection path in :firemore/path. Similarily, each document with have the
document path in firemore-path.

```
Usage:
(hydrate structure-map)
(hydrate atm structure-map)

Returns a atom that will be built and updated from the supplied Firestore
references. 2 argument version can be used to update an existing atom to
the new structured-map.

Important: Close with (-> <returned_atom> deref meta :close (apply [])). Failure
to close the event machine that updates this atom will result in a memory leak.
```

```
Usage:
(realize-collection chan)

Returns a atom containing a map. chan is a channel that sequences a collection
of documents. As the chan has documents marked as being added, updated, and
deleted, they will be correspondingly updated in the realized atom.
```

# <a id="api"></a>API

# <a id="contributing"></a>Contributing

Pull Request are always welcome and appreciated. If you want to discuss firemore, I am available most readily:
1. On [clojurians.slack.com under #firemore](https://clojurians.slack.com/messages/C073DKH9P/).
1. Through the [issue tracking system](https://github.com/samedhi/firemore/issues).
1. By email at stephen@read-line.com .

# <a id="credits"></a>Credits

[Stephen Cagle](https://samedhi.github.io/) is a Senior Software Engineer at [Dividend Finance](https://www.dividendfinance.com/) in San Francisco, CA. He is the original (currently only, but always accepting PRs!) creator/maintainer of firemore.
[@github](https://github.com/samedhi)
[@linkedin](https://www.linkedin.com/in/stephen-cagle-92b895102/)

![Man (Stephen Cagle) holding beer & small dog (Chihuahua)](asset/img/stephen_and_nugget.jpg)

# <a id="License"></a>License

MIT License

Copyright (c) 2019 Stephen Cagle
