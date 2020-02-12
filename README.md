# firemore

**Firemore** is a library for writing [clojurescript](https://clojurescript.org/) applications using [Google Firestore](https://cloud.google.com/firestore).

Please go to [firemore.org](https://firemore.org) for the interactive version of this documentation.

Main features include:
1. A direct API to the [Firestore Database](https://firebase.google.com/docs/firestore).
1. Automatic (and customizable) conversions between clojure maps and Firestore "json" documents.
1. A channels based API for getting and observing Firestore documents.
1. A binding between the Firestore Cloud database and a local clojure atom (great for om/re-frame/reagent).

# Do It Live!

Please go to [firemore.org](https://firemore.org) for the interactive version of this documentation.

# Table of Contents
1. [Getting Started](#getting_started)
1. [Usage](#usage)
1. [API](#api)
1. [Contributing](#contributing)
1. [Credits](#credits)
1. [License](#license)

# <a id="getting_started"></a>Getting Started

To use firemore in an existing project, add this to your dependencies in project.clj ([lein](https://github.com/technomancy/leiningen)) or build.boot ([boot](https://github.com/boot-clj/boot)).

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.samedhi/firemore.svg)](https://clojars.org/org.clojars.samedhi/firemore)

#### Additional Statuses

| What?                                                                                                                                        | Why?                         |
|----------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|
| [![Actions Status](https://github.com/samedhi/firemore/workflows/Run%20Test/badge.svg)](https://github.com/samedhi/firemore/actions)         | Do the test pass?            |
| [![Actions Status](HTTP://github.com/samedhi/firemore/workflows/Periodic%20Test/badge.svg)](https://github.com/samedhi/firemore/actions)     | Do the test pass right now?  |
| [![Actions Status](https://github.com/samedhi/firemore/workflows/Master%20on%20Push/badge.svg)](https://github.com/samedhi/firemore/actions) | Did master deploy correctly? |

# <a id="usage"></a>Usage

## Example
[firemore.org](https://firemore.org) contains interactive code demonstrating most features; much thanks to [Klipse](https://github.com/viebel/klipse) for making this possible.

## API
[API for most recent version](https://firemore.org/pages/api/)

## Test 
[Run the test directly in your browser](https://firemore.org/pages/test/)

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
