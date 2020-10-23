/*
 * Copyright (c) 2015-2020 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.scalatracker

/**
 * Trait for all acceptable Snowplow platforms
 */
sealed trait Platform {
  val abbreviation: String
}

case object Web extends Platform {
  val abbreviation = "web"
}

case object Mobile extends Platform {
  val abbreviation = "mob"
}

case object Desktop extends Platform {
  val abbreviation = "pc"
}

case object Server extends Platform {
  val abbreviation = "srv"
}

case object General extends Platform {
  val abbreviation = "app"
}

case object Tv extends Platform {
  val abbreviation = "tv"
}

case object Console extends Platform {
  val abbreviation = "cnsl"
}

case object InternetOfThings extends Platform {
  val abbreviation = "iot"
}
