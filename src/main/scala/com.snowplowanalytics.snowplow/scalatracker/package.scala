/*
 * Copyright (c) 2015-2018 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.snowplow

import org.json4s.JValue

import com.snowplowanalytics.iglu.core.{SchemaKey, SelfDescribingData }

package object scalatracker {

  /** Tracker-specific self-describing JSON */
  type SelfDescribingJson = SelfDescribingData[JValue]

  /** Backward-compatibility */
  object SelfDescribingJson {
    @deprecated("Use com.snowplowanalytics.iglu.core.SchemaKey instead", "0.5.0")
    def apply(key: String, data: JValue): SelfDescribingJson =
      SelfDescribingData[JValue](SchemaKey.fromUri(key).getOrElse(throw new RuntimeException(s"Invalid SchemaKey $key. Use com.snowplowanalytics.iglu.core.SchemaKey")), data)
  }
}
