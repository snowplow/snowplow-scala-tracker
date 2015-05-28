# snowplow-scala-tracker
[![License][license-image]][license]

## Overview

Snowplow event tracker for Scala. Add analytics to your Scala apps and servers using the **[Snowplow] [snowplow]** event tracker for **[Scala][scala]**.

## Quickstart

Assuming git, **[Vagrant] [vagrant-install]** and **[VirtualBox] [virtualbox-install]** installed:

```bash
 host$ git clone https://github.com/snowplow/snowplow-scala-tracker.git
 host$ cd snowplow-scala-tracker
 host$ vagrant up && vagrant ssh
guest$ cd /vagrant
guest$ sbt test
```

## Publishing

```bash
 host$ vagrant push
```

## Find out more

| Technical Docs                  | Setup Guide               | Roadmap                 | Contributing                      |
|---------------------------------|---------------------------|-------------------------|-----------------------------------|
| ![i1] [techdocs-image]          | ![i2] [setup-image]       | ![i3] [roadmap-image]   | ![i4] [contributing-image]        |
| **[Technical Docs] [techdocs]** | **[Setup Guide] [setup]** | **[Roadmap] [roadmap]** | **[Contributing] [contributing]** |

## Copyright and license

The Snowplow Scala Tracker is copyright 2015 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0] [license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[license]: http://www.apache.org/licenses/LICENSE-2.0

[snowplow]: http://snowplowanalytics.com
[scala]: http://www.scala-lang.org/

[vagrant-install]: http://docs.vagrantup.com/v2/installation/index.html
[virtualbox-install]: https://www.virtualbox.org/wiki/Downloads

[techdocs-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/techdocs.png
[setup-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/setup.png
[roadmap-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/roadmap.png
[contributing-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/contributing.png

[techdocs]: https://github.com/snowplow/snowplow/wiki/Scala-Tracker
[setup]: https://github.com/snowplow/snowplow/wiki/Scala-Tracker-Setup
[roadmap]: https://github.com/snowplow/snowplow/wiki/Scala-Tracker-Roadmap
[contributing]: https://github.com/snowplow/snowplow/wiki/Scala-Tracker-Contributing
