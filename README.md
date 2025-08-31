# TMDb-shelf  
[![Actions Status](https://github.com/newca12/TMDb-shelf/actions/workflows/scala.yml/badge.svg)](https://github.com/newca12/TMDb-shelf/actions) [![codecov.io](https://codecov.io/github/newca12/TMDb-shelf/coverage.svg?branch=master)](https://codecov.io/github/newca12/TMDb-shelf?branch=master) [![Ohloh](http://www.openhub.net/p/TMDb-shelf/widgets/project_thin_badge.gif)](https://www.openhub.net/p/TMDb-shelf)

![Image](./screenshot.png?raw=true)

### About ###
TMDb-shelf is a totally asynchronous JavaFX client written in Scala for [The Movie Database][1] (TMDb).  
It is built heavily on :
* [TMDb-async-client][2]  
* [JavaFX][3]
* [Akka][4]

TMDb-shelf is an EDLA project.

The purpose of [edla.org](http://www.edla.org) is to promote the state of the art in various domains.

### Requirements ###
1. Java 23 is required since TMDb-shelf 1.3.26
2. optional : mediainfo (in /usr/bin)

### API Key ###
You will need an API key to The Movie Database to access the API.  To obtain a key, follow these steps:

1. Register for and verify an [account](https://www.themoviedb.org/account/signup).
2. [Log](https://www.themoviedb.org/login) into your account
3. Select the API section on left side of your account page.
4. Click on the link to generate a new API key and follow the instructions.

### Build  and run ###

1. sbt assembly
2. java -jar TMDb-shelf/target/scala-2.13/TMDb-shelf-assembly-x.y.z.jar

### Packaging ###

Example for linux:
1. jpackage --type deb --name TMDb-shelf --input ./target/scala-2.13 --main-jar TMDb-shelf-assembly-x.y.z.jar --icon ./movie.png

for macOS:
1. jpackage --type dmg  --name TMDb-shelf --input  ./target/scala-2.13 --main-jar TMDb-shelf-assembly-x.y.z.jar --icon ./movie.icns

for Windows:
1. export PATH=$PATH:"/c/Program Files (x86)/WiX Toolset v3.11/bin"
2. jpackage.exe --type msi --name TMDb-shelf --input ./target/scala-2.13 --main-jar TMDb-shelf-assembly-x.y.z.jar --icon movie.ico

### License ###
© 2014-2025 Olivier ROLAND. Distributed under the GPLv3 License.

[1]: https://www.themoviedb.org/
[2]: https://github.com/newca12/TMDb-async-client
[3]: https://openjfx.io/
[4]: https://akka.io/
