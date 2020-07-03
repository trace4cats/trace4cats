FROM debian

COPY trace4cats-collector-lite /bin/

RUN useradd --system --create-home --uid 9000 --gid 0 trace4cats
USER 9000:0

ENTRYPOINT ["/bin/trace4cats-collector-lite"]