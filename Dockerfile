## Docker GROBID-superconductors image using deep learning models and/or CRF models, and various python modules
## Borrowed from https://github.com/kermitt2/grobid/blob/master/Dockerfile.delft
## See https://grobid.readthedocs.io/en/latest/Grobid-docker/

## usage example with grobi version 0.6.2-SNAPSHOT: https://github.com/kermitt2/grobid/blob/master/Dockerfile.delft

## docker build -t lfoppiano/grobid-superconductors:0.2.0-SNAPSHOT --build-arg GROBID_VERSION=0.2.0-SNAPSHOT --file Dockerfile .

## no GPU:
## docker run -t --rm --init -p 8072:8072 -p 8073:8073 -v /home/lopez/grobid/grobid-home/config/grobid.properties:/opt/grobid/grobid-home/config/grobid.properties:ro  lfoppiano/grobid-superconductors:0.2.0-SNAPSHOT

## allocate all available GPUs (only Linux with proper nvidia driver installed on host machine):
## docker run --rm --gpus all --init -p 8072:8072 -p 8073:8073 -v /home/lopez/obid/grobid-home/config/grobid.properties:/opt/grobid/grobid-home/config/grobid.properties:ro  lfoppiano/grobid-superconductors:0.2.0-SNAPSHOT

# -------------------
# build builder image
# -------------------

FROM openjdk:8u275-jdk as builder

USER root

RUN apt-get update && \
    apt-get -y --no-install-recommends install apt-utils libxml2 git

RUN git clone https://github.com/kermitt2/grobid.git /opt/grobid-source
WORKDIR /opt/grobid-source

RUN git clone https://github.com/kermitt2/grobid-quantities.git /opt/grobid-source/grobid-quantities
WORKDIR /opt/grobid-source/grobid-quantities
RUN ./gradlew copyModels --no-daemon --info --stacktrace

WORKDIR /opt/grobid-source
RUN mkdir -p grobid-superconductors
RUN git clone https://github.com/lfoppiano/grobid-superconductors.git ./grobid-superconductors

WORKDIR /opt/grobid-source/grobid-superconductors
RUN git clone https://github.com/lfoppiano/grobid-superconductors-tools.git ./resources/web

RUN ./gradlew clean assemble --no-daemon  --info --stacktrace
RUN ./gradlew copyModels --no-daemon --info --stacktrace

WORKDIR /opt

# -------------------
# build runtime image
# -------------------

# use NVIDIA Container Toolkit to automatically recognize possible GPU drivers on the host machine
FROM nvidia/cuda:10.2-base-ubuntu18.04
CMD nvidia-smi

# setting locale is likely useless but to be sure
ENV LANG C.UTF-8

# install JRE 8, python and other dependencies
RUN apt-get update && \
    apt-get -y --no-install-recommends install apt-utils build-essential gcc libxml2 unzip curl \
    openjdk-8-jre-headless ca-certificates-java \
    git \
    musl gfortran \
    python3.7 python3.7-venv python3.7-dev python3.7-distutil \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/grobid

ENV VIRTUAL_ENV=/opt/grobid/venv
RUN python3.7 -m venv $VIRTUAL_ENV
ENV PATH="$VIRTUAL_ENV/bin:$PATH"
RUN pip --version

RUN mkdir -p /opt/grobid/grobid-superconductors
COPY --from=builder /opt/grobid-source/grobid-home ./grobid-home/
COPY --from=builder /opt/grobid-source/grobid-superconductors/build/libs/* ./grobid-superconductors
COPY --from=builder /opt/grobid-source/grobid-superconductors/resources/config/config.yml ./grobid-superconductors

RUN mkdir -p /opt/grobid/grobid-home/tmp

RUN rm -rf /opt/grobid/grobid-home/pdf2xml/lin-32 /opt/grobid/grobid-home/pdf2xml/mac-64 /opt/grobid/grobid-home/pdf2xml/win-* /opt/grobid/grobid-home/lib/lin-32 /opt/grobid/grobid-home/lib/win-*

RUN chmod -R 755 /opt/grobid/grobid-home/pdf2xml/
RUN chmod 777 /opt/grobid/grobid-home/tmp

# below to allow logs to be written in the container
# RUN mkdir -p logs

VOLUME ["/opt/grobid/grobid-home/tmp"]

RUN python3 -m pip install pip --upgrade
RUN pip --version
# install DeLFT via pypi
## RUN pip3 install requests delft==0.2.6
# link the data directory to /data
# the current working directory will most likely be /opt/grobid
##RUN mkdir -p /data \
##    && ln -s /data /opt/grobid/data \
##    && ln -s /data ./data

# install DeLFT by cloning the repo - only for dev time!
#RUN git clone https://github.com/kermitt2/delft
#WORKDIR /opt/delft
#RUN pip3 install -r requirements.txt
# cleaning useless delft data
#RUN rm -rf data/sequenceLabelling data/textClassification data/test data/models/sequenceLabelling data/models/textClassification .git

# Install requirements
WORKDIR /opt/grobid
COPY --from=builder /opt/grobid-source/grobid-superconductors/requirements.txt /opt/grobid/
RUN pip --version
RUN pip install -r requirements.txt

# install linking components
RUN mkdir -p /opt/grobid/grobid-superconductors-tools
COPY --from=builder /opt/grobid-source/grobid-superconductors/resources/web/commons /opt/grobid/grobid-superconductors-tools/commons
RUN pip install -e /opt/grobid/grobid-superconductors-tools/commons
COPY --from=builder /opt/grobid-source/grobid-superconductors/resources/web/linking /opt/grobid/grobid-superconductors-tools/linking
RUN pip install -e /opt/grobid/grobid-superconductors-tools/linking
COPY --from=builder /opt/grobid-source/grobid-superconductors/resources/web/materialParser /opt/grobid/grobid-superconductors-tools/materialParser
RUN pip install git+https://github.com/lfoppiano/MaterialParser
RUN pip install -e /opt/grobid/grobid-superconductors-tools/materialParser

# disable python warnings (and fix logging)
ENV PYTHONWARNINGS="ignore"

ENV JAVA_OPTS=-Xmx4g

# Add Tini
ENV TINI_VERSION v0.18.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "-s", "--"]

# install jep (and temporarily the matching JDK)
ENV TEMP_JDK_HOME=/tmp/jdk-${JAVA_VERSION}
ENV JDK_URL=https://github.com/AdoptOpenJDK/openjdk8-upstream-binaries/releases/download/jdk8u212-b04/OpenJDK8U-x64_linux_8u212b04.tar.gz
RUN curl --fail --show-error --location -q ${JDK_URL} -o /tmp/openjdk.tar.gz \
    && ls -lh /tmp/openjdk.tar.gz \
    && mkdir - "${TEMP_JDK_HOME}" \
    && tar --extract \
        --file /tmp/openjdk.tar.gz \
        --directory "${TEMP_JDK_HOME}" \
        --strip-components 1 \
        --no-same-owner \
    && JAVA_HOME=${TEMP_JDK_HOME} pip3 install jep==3.9.1 \
    && rm -f /tmp/openjdk.tar.gz \
    && rm -rf "${TEMP_JDK_HOME}"
ENV LD_LIBRARY_PATH=/opt/grobid/venv/lib/python3.7/dist-packages/jep:${LD_LIBRARY_PATH}
# remove libjep.so because we are providng our own version in the virtual env
RUN rm /opt/grobid/grobid-home/lib/lin-64/libjep.so

# preload embeddings, for GROBID all the RNN models use glove-840B (default for the script), ELMo is currently not loaded 
# to be done: mechanism to download GROBID fine-tuned models based on SciBERT if selected

##COPY --from=builder /opt/grobid-source/grobid-home/scripts/preload_embeddings.py .
##COPY --from=builder /opt/grobid-source/grobid-home/config/embedding-registry.json .
##RUN python3 preload_embeddings.py
##RUN ln -s /opt/grobid /opt/delft

RUN sed -i 's/pythonVirtualEnv:.*/pythonVirtualEnv: \/opt\/grobid\/venv/g' grobid-superconductors/config.yml
RUN sed -i 's/grobidHome:.*/grobidHome: grobid-home/g' grobid-superconductors/config.yml

CMD ["java", "-jar", "grobid-superconductors/grobid-superconductors-0.2.0-SNAPSHOT-onejar.jar", "server", "grobid-superconductors/config.yml"]

ARG GROBID_VERSION


LABEL \
    authors="Luca Foppiano with the support of NIMS (National Institute for Materials Science, Tsukuba, Japan)" \
    org.label-schema.name="grobid-superconductors" \
    org.label-schema.description="Image with grobid-superconductors service" \
    org.label-schema.url="https://github.com/lfoppiano/grobid-superconductors" \
    org.label-schema.version=${GROBID_VERSION}


## Docker tricks:

# - remove all stopped containers
# > docker rm $(docker ps -a -q)

# - remove all unused images
# > docker rmi $(docker images --filter "dangling=true" -q --no-trunc)

# - remove all untagged images
# > docker rmi $(docker images | grep "^<none>" | awk "{print $3}")

# - "Cannot connect to the Docker daemon. Is the docker daemon running on this host?"
# > docker-machine restart

