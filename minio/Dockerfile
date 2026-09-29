FROM alpine:3.20

RUN apk add --no-cache ca-certificates curl libc6-compat && \
    curl -sSL https://dl.min.io/server/minio/release/linux-amd64/minio -o /usr/bin/minio && \
    chmod +x /usr/bin/minio && \
    mkdir -p /data

EXPOSE 9000 9001

VOLUME ["/data"]

ENTRYPOINT ["/usr/bin/minio"]
CMD ["server", "/data", "--console-address", ":9001"]
