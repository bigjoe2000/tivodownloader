TiVo Extractor

I wrote this program to automate the process of pulling all my recording off my Tivo after cancelling my cable service. I've been using a TiVo for over 15 years so this is a sad day for me. But I find that I consume most content via streaming services now and there's no point in paying $100 for tv service I rarely use. I recommend the Walmart Onn as a streaming device because its cheap and fast.

The easiest way to get this up and running:

Edit run.sh and:
 1. edit /path/to/downloads to point to the absolute path of where you want to store the downloaded files
 2. edit the ip address to point to your tivo (add an optional port number if necessary ex. 192.168.1.23:8476)
 3. edit the MAK to your Tivo's Media Access Key

In terminal, type `./run.sh`

Note that this will rebuild the image every time, but with docker's caching mechanism, it won't take long. You can always modify this to skip the docker build and just put in the image name.

Thanks to the good people at the [kmttg project](https://sourceforge.net/p/kmttg/wiki/config_Programs/) for the inspiration and Http.java which I totally stole.


Future work for this project:
1. See the github [issues page](https://github.com/bigjoe2000/tivodownloader/issues) for more
