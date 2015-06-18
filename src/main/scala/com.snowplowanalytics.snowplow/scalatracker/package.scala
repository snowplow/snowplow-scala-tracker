package com.snowplowanalytics.snowplow

package object scalatracker {

  type ??? = Any

  def notSupported(message: String) = throw new Error(message)

  /* Tracker event parameters */

  // General
  val SCHEMA = "schema"
  val DATA = "data"
  val EVENT = "e"
  val EID = "eid"
  val TIMESTAMP = "dtm"
  val TRACKER_VERSION = "tv"
  val APPID = "aid"
  val NAMESPACE = "tna"

  val UID = "uid"
  val CONTEXT = "co"
  val CONTEXT_ENCODED = "cx"
  val UNSTRUCTURED = "ue_pr"
  val UNSTRUCTURED_ENCODED = "ue_px"

  // Subject class
  val PLATFORM = "p"
  val RESOLUTION = "res"
  val VIEWPORT = "vp"
  val COLOR_DEPTH = "cd"
  val TIMEZONE = "tz"
  val LANGUAGE = "lang"

  // Page view
  val PAGE_URL = "url"
  val PAGE_TITLE = "page"
  val PAGE_REFR = "refr"

  // Structured Event 
  val SE_CATEGORY = "se_ca"
  val SE_ACTION = "se_ac"
  val SE_LABEL = "se_la"
  val SE_PROPERTY = "se_pr"
  val SE_VALUE = "se_va"

  // Ecomm Transaction
  val TR_ID = "tr_id"
  val TR_TOTAL = "tr_tt"
  val TR_AFFILIATION = "tr_af"
  val TR_TAX = "tr_tax"
  val TR_SHIPPING = "tr_sh"
  val TR_CITY = "tr_ci"
  val TR_STATE = "tr_st"
  val TR_COUNTRY = "tr_co"
  val TR_CURRENCY = "tr_cu"

  // Transaction Item
  val TI_ITEM_ID = "ti_id"
  val TI_ITEM_SKU = "ti_sk"
  val TI_ITEM_NAME = "ti_nm"
  val TI_ITEM_CATEGORY = "ti_ca"
  val TI_ITEM_PRICE = "ti_pr"
  val TI_ITEM_QUANTITY = "ti_qu"
  val TI_ITEM_CURRENTY = "ti_cu"

  // Screen View
  val SV_ID = "id"
  val SV_NAME = "name"

  object Constants {
    val PROTOCOL_VENDOR = "com.snowplowanalytics.snowplow"
    val PROTOCOL_VERSION = "tp2"

    val SCHEMA_PAYLOAD_DATA = "iglu:com.snowplowanalytics.snowplow/payload_data/jsonschema/1-0-0"
    val SCHEMA_CONTEXTS = "iglu:com.snowplowanalytics.snowplow/contexts/jsonschema/1-0-0"
    val SCHEMA_UNSTRUCT_EVENT = "iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0"
    val SCHEMA_SCREEN_VIEW = "iglu:com.snowplowanalytics.snowplow/screen_view/jsonschema/1-0-0"

    val EVENT_UNSTRUCTURED = "ue"
    val EVENT_STRUCTURED = "se"
    val EVENT_PAGE_VIEW = "pv"
    val EVENT_ECOMM = "tr"
    val EVENT_ECOMM_ITEM = "ti"
  }
}