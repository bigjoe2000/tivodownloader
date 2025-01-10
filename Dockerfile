FROM ubuntu:24.04

RUN apt-get update
RUN apt-get install -y openjdk-17-jdk openjdk-17-jre
RUN apt-get install -y maven
RUN apt-get install -y ffmpeg
RUN apt-get install -y git
RUN apt-get install -y build-essential libargtable2-dev libsdl1.2-dev libavformat-dev autoconf libtool libswscale-dev

RUN git clone https://github.com/erikkaashoek/Comskip.git
WORKDIR /Comskip
COPY comskip.ini /Comskip/
RUN ./autogen.sh && ./configure && make && make install

RUN mkdir /downloader
COPY pom.xml /downloader/
COPY src /downloader/src

WORKDIR /downloader

ENTRYPOINT ["mvn", "package", "exec:java", "-Dexec.mainClass=org.bigjoe.tivo.App", "-DskipTests"]

