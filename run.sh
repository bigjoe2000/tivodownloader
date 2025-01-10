TIVO_IP=129.168.1.101   # Your TiVo ip address
MAK=1234567890 # Your TiVo Media Access Key
OUTPUT_DIR=/tmp/tivo # Directory where downloads should be stored
M2_CACHE_DIR=/tmp/.m2 # Directory where maven can download libraries should be stored
LIMIT=0 # Max files to process (0 for unlimited)
DELETE_FROM_TIVO=false # Whether or not to delete the files from the TiVo after downloading. Enable by setting this to "true". This is not yet implemented

mkdir -p $OUTPUT_DIR
mkdir -p $M2_CACHE_DIR

docker run -it -v $OUTPUT_DIR:/downloads -v $M2_CACHE_DIR:/root/.m2 -u$(docker build -q .) -Dexec.args="$TIVO_IP $MAK $LIMIT $DELETE_FROM_TIVO"
#docker run -it -v $OUTPUT_DIR:/downloads -v $M2_CACHE_DIR:/root/.m2 --entrypoint /usr/bin/bash $(docker build -q .) 
