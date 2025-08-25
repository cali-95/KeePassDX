package com.kunzisoft.keepass.credentialprovider.passkey.util

import org.junit.Test


class AppRelyingPartyRelationTest {

    @Test
    fun testParseJsonSmall() {
        assert(AppRelyingPartyRelation.parseJsonSafe("").isEmpty())
        assert(AppRelyingPartyRelation.parseJsonSafe("aaaa!aaaa").isEmpty())
        assert(AppRelyingPartyRelation.parseJsonSafe("{{{{{{}}}").isEmpty())
        assert(AppRelyingPartyRelation.parseJsonSafe("{}").isEmpty())
        assert(AppRelyingPartyRelation.parseJsonSafe("[]").isEmpty())
    }

    @Test
    fun testParseJsonBig() {

        val json = """
            [
              {
                "relation": [
                  "delegate_permission/common.handle_all_urls"
                ],
                "target": {
                  "namespace": "android_app",
                  "package_name": "com.google.android.calendar",
                  "sha256_cert_fingerprints": [
                    "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83"
                  ]
                }
              },
              {
                "relation": [
                  "delegate_permission/common.handle_all_urls",
                  "delegate_permission/common.use_as_origin"
                ],
                "target": {
                  "namespace": "android_app",
                  "package_name": "com.google.android.googlequicksearchbox",
                  "sha256_cert_fingerprints": [
                    "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83",
                    "19:75:B2:F1:71:77:BC:89:A5:DF:F3:1F:9E:64:A6:CA:E2:81:A5:3D:C1:D1:D5:9B:1D:14:7F:E1:C8:2A:FA:00"
                  ]
                }
              },
                {
                  "relation": [
                    "delegate_permission/common.handle_all_urls",
                    "delegate_permission/common.get_login_creds"
                  ],
                  "target": {
                    "namespace": "android_app",
                    "package_name": "com.google.android.gms",
                    "sha256_cert_fingerprints": [
                      "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83",
                      "19:75:B2:F1:71:77:BC:89:A5:DF:F3:1F:9E:64:A6:CA:E2:81:A5:3D:C1:D1:D5:9B:1D:14:7F:E1:C8:2A:FA:00",
                      "7C:E8:3C:1B:71:F3:D5:72:FE:D0:4C:8D:40:C5:CB:10:FF:75:E6:D8:7D:9D:F6:FB:D5:3F:04:68:C2:90:50:53",
                      "D2:2C:C5:00:29:9F:B2:28:73:A0:1A:01:0D:E1:C8:2F:BE:4D:06:11:19:B9:48:14:DD:30:1D:AB:50:CB:76:78",
                      "5F:23:91:27:7B:1D:BD:48:90:00:46:7E:4C:2F:A6:AF:80:24:30:08:04:57:DC:E2:F6:18:99:2E:9D:FB:54:02",
                      "8F:D4:A0:E7:B8:EB:7C:72:6D:2D:AC:79:44:5B:2C:79:31:F6:2B:7E:B6:92:F5:04:4B:DC:FD:FA:AD:07:0A:36"
                    ]
                  }
                }
              ]
        """.trimIndent()
        val r = AppRelyingPartyRelation.parseJsonSafe(json)
        assert(r.size == 6)

        assert(r.any { f -> f[0] == 0xf0.toByte() })
    }

}
