# firemore

**Firemore** is a library for writing [clojure](https://clojure.org/)([script](https://clojurescript.org/)) applications using [Google Firestore](https://cloud.google.com/firestore).

Main features include:
1. A direct API to the [Firestore Database](https://firebase.google.com/docs/firestore).
1. Automatic (and customizable) conversions between clojure maps and Firestore "json" documents.
1. A channels based API for getting and observing Firestore documents.
1. A binding between the Firestore Cloud database and a local clojure atom.

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

1. (firemore/hydrate channel-map) -> atom that will update with the most recent state of all channels, great for use in om/reagent/quiescent/re-frame. atom will contain the following metadata.
    1. :clear - (fn []) -> (update {}) - Sets the channel-map to be the empty map.
    2. :update - (fn [new-channel-map] ...) Takes a new channel map that is now the map that will now be the updated channel map.2
1. A hydrated atom with no channels being observed in channel-map has no running go machine. Otherwise there is a go-machine that observes changes. For this reason you should :clear the channel map before releasing your reference to it to avoid a memory leak.
1. Use clojure 'read' functions (`get`, `get-in`, keyword lookup) to read the derefed atom.
1. Use clojure 'write' functions (`assoc`, `assoc-in`, `update-in`, et all) with `swap!` to update the atom. Changes to the atom are automatically persisted to the Firestore Cloud Database.
    1. Changes to the atom will synchronously assume that the write will succeed and update immediately. The atom will later revert the write if the Firestore Cloud Database rejects the write.
    1. Use `(transactional! atm ...)` to write a transaction to Firestore. This will return a channel with either the new state of the atom or a failure.

    1. (firemore/get :cities :austin) -> chan to retrieve the `<firestore_db>/cities/austin` document.
    1. (firemore/watch :cities :austin) -> chan to get and then watch `<firestore_db>/cities/austin` for updates.
    1. `:firestore/deleted` to indicate that the entity was deleted from the `<firestore_db>`.
    1. Close the channel to stop listening at that path.


    ```
    {:user [:user <my-user-id>]
     :favorites
       {:movie [:movie :interstellar]
        :food [:food :pizza]}
     :friends [:user <my-user-id> :friends]}

    =>

    {:user {:path [:user <my-user-id>]
            :first "Stephen"
            :last "Cagle"}
     :favorites
     {:movie {:path [:movie :interstellar]
              :title "Interstellar"}
      :food {:path [:food :pizza]
             :ingredients [:cheese :sauce :dough]}}
     :friends [<user-id-1> <user-id-2> <user-id-3>]}
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
