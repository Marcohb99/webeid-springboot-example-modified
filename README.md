# Marco Antonio Hurtado Bandrés <br/> Trabajo Fin de Grado 2021 <br/> Final Year Dissertation  

![logo etsii uma](https://www.uma.es/etsi-informatica/navegador_de_ficheros/Informatica/descargar/Logos/COLOR_FONDOBLANCO_INFORMATICA.png)

# Web eID Spring Boot example Modified
This is one of the components for my final year dissertation, which is a slight modification
of the original [web eid spring boot example](https://github.com/web-eid/web-eid-spring-boot-example).

## Table of contents
* [Introduction](#introduction)
* [Modifications](#modifications)
* [Requirements](#requirements)
* [Setup](#setup)
* [Configuration](#configuration)

## Introduction

This example works almost the same way as the [Web eid spring boot example](https://github.com/web-eid/web-eid-spring-boot-example), with some minor changes.

Note that this modification is part of a **proof of concept**, therefore, some things may cause errors or be missing, but it has been tested with the rest of the components, and they work well. The components of this project are:
* The **modified native application**, developed in Python, which works kind of the same way as the original C++ native application, but using eID certificate files instead of eID smart cards. It also adds the functionality of storing eID usage records in local json files as well as the signed metadata, and sending the record posts to the eID record server to store them remotely.
* This **modified spring boot example**, only modified to accept spanish AC-FNMT Usuarios and seg-social ACGISS eID certificates, which can be seen [here](https://webeidspringexsamplemodified.herokuapp.com/).
* The **modified Web eID extension**, only modified to fit the new structure with the record server added.
* The [**eID record server**](https://eidrecordserver.herokuapp.com): a new web application made from the original Web eID spring boot example which will track all the activity made with eID certificates and allows users to see a list of usages, download them and receive email notifications when their certificates are used. This component has been developed to add secure digital signature delegation.


## Modifications
This component has a few modifications, because one of the goals of this proof of concept
was to show that it was possible to keep and add functionality without having to change too many things. 
The main features added, and the changes made have been mainly done to fit the new signing process.
In fact, the integration wit the eID record server (the main component to support eID delegation) required
no changes in this project.

Changes made:
1. The inclusion of the Spanish CAs [AC-FNMT Usuarios](http://www.cert.fnmt.es/certs/ACUSU.crt) and
   [ACGISS](http://www.seg-social.es/ACGISS/SUBCA_GISS01) certificates.
2. The **keystore** has been regenerated with these certificates.
3. To fit the new signing process, the whole data is sent to the native app instead of the hash of the example data to sign, because the data being hashed
   is like a little metadata file.
4. There was a problem with the [digidoc4j library](https://github.com/open-eid/digidoc4j) when signing and validating OCSP response, so in order to solve it and after
trying several things and debugging for a long time:
    - The whole digidoc4j library had to be included, and the line causing the problem line was commented to check what happens
    when you sign without validating the origin.
    - Due to the fact that the problem was in relation with OCSP response and no request was being sent when signing
      (checked with wireshark), the OCSP request functionality has been extracted from the authtoken validation library and
      then included in the *CertificateValidationService*, so it's called when signing too. Note that **this problem happened even
      when using Estonian certificates.** The result of commenting the line mentioned is that neither the Digidoc desktop app
      nor Spanish Autofirma app fully recognize the signature.
5. To try to solve the above problem, there was an attempt to make the container building and signing process with the 
[@firma library](https://github.com/ctt-gob-es/clienteafirma), but hasn't worked yet. Further research is needed.
   
## Requirements
If you want to test this component working in heroku, just head up to https://webeidspringexsamplemodified.herokuapp.com <br/>
You just need the native app and the extension to test the *suite* because the eID record server is
also in heroku: https://eidrecordserver.herokuapp.com


In order to make the app work **locally** you just need the following.

### Browser
This app has only been tested in **Firefox**.

The following content is the same as the original project README.

## Setup

Note that although the browser considers localhost to be a secure context, it does not have a certificate,
therefore you need to use e.g. Ngrok.io for local testing.

Download ngrok and run locally using two parameters:
```sh
ngrok http 8080
```

## Configuration

Open [application.yaml](src/main/resources/application.yaml) file and set up the `local-origin` and `fingerprint` fields:

- The `local-origin` field must contain the url, which was generated by ngrok.
- The `fingerprint` field must contain the SHA256 fingerprint of the certificate, which you can retrieve from the browser by navigating to the ngrok url and exploring the server certificate details. Here is a [quick guide](https://www.globalsign.com/en/blog/how-to-view-ssl-certificate-details) and for different browsers.

```yaml
spring:
  profiles: dev
  servlet:
    multipart:
      max-file-size: 5000KB
      max-request-size: 5000KB
token:
  validation:
    local-origin: "https://ade0973a6557.ngrok.io"
    fingerprint: "11:D8:AE:60:EC:19:10:C7:94:D7:4C:82:C8:0D:96:B2:07:88:B5:6A:D2:65:FF:F9:B5:14:C8:75:F7:90:08:E1"
    keystore-password: "changeit"
```

Additionally, you can set up the keystore password, and the maximum size of the files to be uploaded.

## Running

Once the `local-origin` and `fingerprint` fields are set, you can run the application with the following command:
```sh
./mvnw spring-boot:run
```
and then navigate the browser to the url, that was generated by ngrok.

**Note:** in linux, you may need to add execution permissions to `mvnw`
```bash
chmod +x ./mvnw
```

After signing, you can open the containers, for example, with:
- [Digidoc client](https://www.microsoft.com/en-us/p/digidoc4-client/9pfpfk4dj1s6)
- [Autofirma](https://firmaelectronica.gob.es/Home/Descargas.html):
    1. Open the container as a zip and extract it.
    2. In autofirma, open the file META-INF/signatures0.xml

## Testing

In case you don't have the Web eID extension installed or want to test `web-eid.js` message handling manually,
you can use the browser developer tools to mock the authentication response from the extension with
`window.postMessage()` as follows:

1. Click *Authenticate*
2. Paste the following snippet into developer tools console:

```js
var loginResponse = await fetch("/auth/login", {
    method: "POST",
    headers: {
      'Content-Type': 'application/json'
    },
    body: '{"auth-token":"eyJhbGciOiJFUzM4NCIsInR5cCI6IkpXVCIsIng1YyI6WyJNSUlFQXpDQ0EyV2dBd0lCQWdJUU9Xa0JXWE5ESm0xYnlGZDNYc1drdmpBS0JnZ3Foa2pPUFFRREJEQmdNUXN3Q1FZRFZRUUdFd0pGUlRFYk1Ca0dBMVVFQ2d3U1Uwc2dTVVFnVTI5c2RYUnBiMjV6SUVGVE1SY3dGUVlEVlFSaERBNU9WRkpGUlMweE1EYzBOekF4TXpFYk1Ca0dBMVVFQXd3U1ZFVlRWQ0J2WmlCRlUxUkZTVVF5TURFNE1CNFhEVEU0TVRBeE9EQTVOVEEwTjFvWERUSXpNVEF4TnpJeE5UazFPVm93ZnpFTE1Ba0dBMVVFQmhNQ1JVVXhLakFvQmdOVkJBTU1JVXJEbFVWUFVrY3NTa0ZCU3kxTFVrbFRWRXBCVGl3ek9EQXdNVEE0TlRjeE9ERVFNQTRHQTFVRUJBd0hTc09WUlU5U1J6RVdNQlFHQTFVRUtnd05Ta0ZCU3kxTFVrbFRWRXBCVGpFYU1CZ0dBMVVFQlJNUlVFNVBSVVV0TXpnd01ERXdPRFUzTVRnd2RqQVFCZ2NxaGtqT1BRSUJCZ1VyZ1FRQUlnTmlBQVI1azFsWHp2U2VJOU8vMXMxcFp2amhFVzhuSXRKb0cwRUJGeG1MRVk2UzdraTF2RjJRM1RFRHg2ZE56dEkxWHR4OTZjczhyNHpZVHdkaVFvRGc3azNkaVV1UjluVFdHeFFFTU8xRkRvNFk5ZkFtaVBHV1QrK0d1T1ZvWlFZM1h4aWpnZ0hETUlJQnZ6QUpCZ05WSFJNRUFqQUFNQTRHQTFVZER3RUIvd1FFQXdJRGlEQkhCZ05WSFNBRVFEQStNRElHQ3lzR0FRUUJnNUVoQVFJQk1DTXdJUVlJS3dZQkJRVUhBZ0VXRldoMGRIQnpPaTh2ZDNkM0xuTnJMbVZsTDBOUVV6QUlCZ1lFQUk5NkFRSXdId1lEVlIwUkJCZ3dGb0VVTXpnd01ERXdPRFUzTVRoQVpXVnpkR2t1WldVd0hRWURWUjBPQkJZRUZPUXN2VFFKRUJWTU1TbWh5Wlg1YmliWUp1YkFNR0VHQ0NzR0FRVUZCd0VEQkZVd1V6QlJCZ1lFQUk1R0FRVXdSekJGRmo5b2RIUndjem92TDNOckxtVmxMMlZ1TDNKbGNHOXphWFJ2Y25rdlkyOXVaR2wwYVc5dWN5MW1iM0l0ZFhObExXOW1MV05sY25ScFptbGpZWFJsY3k4VEFrVk9NQ0FHQTFVZEpRRUIvd1FXTUJRR0NDc0dBUVVGQndNQ0JnZ3JCZ0VGQlFjREJEQWZCZ05WSFNNRUdEQVdnQlRBaEprcHhFNmZPd0kwOXBuaENsWUFDQ2srZXpCekJnZ3JCZ0VGQlFjQkFRUm5NR1V3TEFZSUt3WUJCUVVITUFHR0lHaDBkSEE2THk5aGFXRXVaR1Z0Ynk1emF5NWxaUzlsYzNSbGFXUXlNREU0TURVR0NDc0dBUVVGQnpBQ2hpbG9kSFJ3T2k4dll5NXpheTVsWlM5VVpYTjBYMjltWDBWVFZFVkpSREl3TVRndVpHVnlMbU55ZERBS0JnZ3Foa2pPUFFRREJBT0Jpd0F3Z1ljQ1FnSDFVc21NZHRMWnRpNTFGcTJRUjR3VWtBd3BzbmhzQlYySFFxVVhGWUJKN0VYbkxDa2FYamRaS2tIcEFCZk0wUUV4N1VVaGFJNGk1M2ppSjdFMVk3V09BQUpCRFg0ejYxcG5pSEphcEkxYmtNSWlKUS90aTdoYThmZEpTTVNwQWRzNUN5SEl5SGtReldsVnk4NmY5bUE3RXUzb1JPLzFxK2VGVXpEYk5OM1Z2eTdnUVdRPSJdfQ.eyJhdWQiOlsiaHR0cHM6Ly9yaWEuZWUiLCJ1cm46Y2VydDpzaGEtMjU2OjZmMGRmMjQ0ZTRhODU2Yjk0YjNiM2I0NzU4MmEwYTUxYTMyZDY3NGRiYzcxMDcyMTFlZDIzZDRiZWM2ZDljNzIiXSwiZXhwIjoiMTU4Njg3MTE2OSIsImlhdCI6IjE1ODY4NzA4NjkiLCJpc3MiOiJ3ZWItZWlkIGFwcCB2MC45LjAtMS1nZTZlODlmYSIsIm5vbmNlIjoiMTIzNDU2NzgxMjM0NTY3ODEyMzQ1Njc4MTIzNDU2NzgiLCJzdWIiOiJKw5VFT1JHLEpBQUstS1JJU1RKQU4sMzgwMDEwODU3MTgifQ.0Y5CdMiSZ14rOnd7sbp-XeBQ7qrJVd21yTmAbiRnzAXtwqW8ZROg4jL4J7bpQ2fwyUz4-dVwLoVRVnxfJY82b8NXuxXrDb-8MXXmVYrMW0q0kPbEzqFbEnPYHjNnKAN0"}'
});

window.postMessage({
    action: webeid.Action.AUTHENTICATE_SUCCESS,
    response: {
        status: 200,
        headers: {},
        body: await loginResponse.json()
    }
});
```

## Testing with curl

```shell script
$ curl 'http://localhost:8080/auth/login' -H 'Content-type: application/json' --data-raw '{"auth-token":"eyJhbGciOiJFUzM4NCIsInR5cCI6IkpXVCIsIng1YyI6WyJNSUlFQXpDQ0EyV2dBd0lCQWdJUU9Xa0JXWE5ESm0xYnlGZDNYc1drdmpBS0JnZ3Foa2pPUFFRREJEQmdNUXN3Q1FZRFZRUUdFd0pGUlRFYk1Ca0dBMVVFQ2d3U1Uwc2dTVVFnVTI5c2RYUnBiMjV6SUVGVE1SY3dGUVlEVlFSaERBNU9WRkpGUlMweE1EYzBOekF4TXpFYk1Ca0dBMVVFQXd3U1ZFVlRWQ0J2WmlCRlUxUkZTVVF5TURFNE1CNFhEVEU0TVRBeE9EQTVOVEEwTjFvWERUSXpNVEF4TnpJeE5UazFPVm93ZnpFTE1Ba0dBMVVFQmhNQ1JVVXhLakFvQmdOVkJBTU1JVXJEbFVWUFVrY3NTa0ZCU3kxTFVrbFRWRXBCVGl3ek9EQXdNVEE0TlRjeE9ERVFNQTRHQTFVRUJBd0hTc09WUlU5U1J6RVdNQlFHQTFVRUtnd05Ta0ZCU3kxTFVrbFRWRXBCVGpFYU1CZ0dBMVVFQlJNUlVFNVBSVVV0TXpnd01ERXdPRFUzTVRnd2RqQVFCZ2NxaGtqT1BRSUJCZ1VyZ1FRQUlnTmlBQVI1azFsWHp2U2VJOU8vMXMxcFp2amhFVzhuSXRKb0cwRUJGeG1MRVk2UzdraTF2RjJRM1RFRHg2ZE56dEkxWHR4OTZjczhyNHpZVHdkaVFvRGc3azNkaVV1UjluVFdHeFFFTU8xRkRvNFk5ZkFtaVBHV1QrK0d1T1ZvWlFZM1h4aWpnZ0hETUlJQnZ6QUpCZ05WSFJNRUFqQUFNQTRHQTFVZER3RUIvd1FFQXdJRGlEQkhCZ05WSFNBRVFEQStNRElHQ3lzR0FRUUJnNUVoQVFJQk1DTXdJUVlJS3dZQkJRVUhBZ0VXRldoMGRIQnpPaTh2ZDNkM0xuTnJMbVZsTDBOUVV6QUlCZ1lFQUk5NkFRSXdId1lEVlIwUkJCZ3dGb0VVTXpnd01ERXdPRFUzTVRoQVpXVnpkR2t1WldVd0hRWURWUjBPQkJZRUZPUXN2VFFKRUJWTU1TbWh5Wlg1YmliWUp1YkFNR0VHQ0NzR0FRVUZCd0VEQkZVd1V6QlJCZ1lFQUk1R0FRVXdSekJGRmo5b2RIUndjem92TDNOckxtVmxMMlZ1TDNKbGNHOXphWFJ2Y25rdlkyOXVaR2wwYVc5dWN5MW1iM0l0ZFhObExXOW1MV05sY25ScFptbGpZWFJsY3k4VEFrVk9NQ0FHQTFVZEpRRUIvd1FXTUJRR0NDc0dBUVVGQndNQ0JnZ3JCZ0VGQlFjREJEQWZCZ05WSFNNRUdEQVdnQlRBaEprcHhFNmZPd0kwOXBuaENsWUFDQ2srZXpCekJnZ3JCZ0VGQlFjQkFRUm5NR1V3TEFZSUt3WUJCUVVITUFHR0lHaDBkSEE2THk5aGFXRXVaR1Z0Ynk1emF5NWxaUzlsYzNSbGFXUXlNREU0TURVR0NDc0dBUVVGQnpBQ2hpbG9kSFJ3T2k4dll5NXpheTVsWlM5VVpYTjBYMjltWDBWVFZFVkpSREl3TVRndVpHVnlMbU55ZERBS0JnZ3Foa2pPUFFRREJBT0Jpd0F3Z1ljQ1FnSDFVc21NZHRMWnRpNTFGcTJRUjR3VWtBd3BzbmhzQlYySFFxVVhGWUJKN0VYbkxDa2FYamRaS2tIcEFCZk0wUUV4N1VVaGFJNGk1M2ppSjdFMVk3V09BQUpCRFg0ejYxcG5pSEphcEkxYmtNSWlKUS90aTdoYThmZEpTTVNwQWRzNUN5SEl5SGtReldsVnk4NmY5bUE3RXUzb1JPLzFxK2VGVXpEYk5OM1Z2eTdnUVdRPSJdfQ.eyJhdWQiOlsiaHR0cHM6Ly9yaWEuZWUiLCJ1cm46Y2VydDpzaGEtMjU2OjZmMGRmMjQ0ZTRhODU2Yjk0YjNiM2I0NzU4MmEwYTUxYTMyZDY3NGRiYzcxMDcyMTFlZDIzZDRiZWM2ZDljNzIiXSwiZXhwIjoiMTU4Njg3MTE2OSIsImlhdCI6IjE1ODY4NzA4NjkiLCJpc3MiOiJ3ZWItZWlkIGFwcCB2MC45LjAtMS1nZTZlODlmYSIsIm5vbmNlIjoiMTIzNDU2NzgxMjM0NTY3ODEyMzQ1Njc4MTIzNDU2NzgiLCJzdWIiOiJKw5VFT1JHLEpBQUstS1JJU1RKQU4sMzgwMDEwODU3MTgifQ.0Y5CdMiSZ14rOnd7sbp-XeBQ7qrJVd21yTmAbiRnzAXtwqW8ZROg4jL4J7bpQ2fwyUz4-dVwLoVRVnxfJY82b8NXuxXrDb-8MXXmVYrMW0q0kPbEzqFbEnPYHjNnKAN0"}'
{"sub":"JÕEORG,JAAK-KRISTJAN,38001085718","auth":["ROLE_USER"]}
```

## Formatting code

We use *prettier-java* for formatting code, install and run it as follows:

```shell script
npm install -g prettier prettier-plugin-java
prettier --write "**/*.{java,js,html}"
```

## Deployment

Build Docker image with Jib as follows:

```sh
mvn com.google.cloud.tools:jib-maven-plugin:dockerBuild
```

and deploy with Docker Compose as follows:

```sh
docker-compose up -d
```
