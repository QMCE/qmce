package rj.qmce.lite.fix;

import android.content.pm.Signature;
import android.util.Base64;

final class AppSignatureCertificate {
    static final String PACKAGE_NAME = "com.tencent.qqlite";
    static final String INSTALLED_PACKAGE_NAME = "rj.qmce.litex";

    private static final String CERTIFICATE_BASE64 =
            "MIICUzCCAbygAwIBAgIES7sDYTANBgkqhkiG9w0BAQUFADBtMQ4wDAYDVQQGEwVDaGluYTEPMA0G\n"
                    + "A1UECAwG5YyX5LqsMQ8wDQYDVQQHDAbljJfkuqwxDzANBgNVBAoMBuiFvuiurzEbMBkGA1UECwwS\n"
                    + "5peg57q/5Lia5Yqh57O757ufMQswCQYDVQQDEwJRUTAgFw0xMDA0MDYwOTQ4MTdaGA8yMjg0MDEy\n"
                    + "MDA5NDgxN1owbTEOMAwGA1UEBhMFQ2hpbmExDzANBgNVBAgMBuWMl+S6rDEPMA0GA1UEBwwG5YyX\n"
                    + "5LqsMQ8wDQYDVQQKDAbohb7orq8xGzAZBgNVBAsMEuaXoOe6v+S4muWKoeezu+e7nzELMAkGA1UE\n"
                    + "AxMCUVEwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAKFel1Yhb2lMWRXgtSkJUlQ2fE5k+u/w\n"
                    + "euE0iNlGYVpY3cMaQV9xfQGe3G0wuWA9Pip7PeCrfgz1Lf7jk3O8Ry+plwJ9eY1Z+B1SWmns8Vbo\n"
                    + "hf0eJ5CSQ4ayIwzJDjt63JVgPdz0xAvccvItsPIWqZw3HTv4nLpleMYGmeig1TaVAgMBAAEwDQYJ\n"
                    + "KoZIhvcNAQEFBQADgYEAlKm4DoBpFkXdQtZhF3WoVfcbzU13y2Co4pQEA1peALIbzF1KViSCEmvZ\n"
                    + "G2sOUHCTd86574wu/RLMixav2aFZ81C7JwsUIE/wZdhDgycgcC4otBSR+8OiBfXy9CUm1n8XYU2K\n"
                    + "l03mSHsshm7+3jtOSaD5FrqjwTNv0u4bFillIEk=\n";

    private AppSignatureCertificate() {
    }

    static Signature createSignature() {
        return new Signature(Base64.decode(CERTIFICATE_BASE64, Base64.DEFAULT));
    }
}
