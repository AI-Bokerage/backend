# ClamAV with the low-memory config (no second database copy during reloads, ~1.2 GB peak instead
# of ~2.4 GB). Used on Railway, which can't mount the config file into an image service.
FROM clamav/clamav-debian:1.4
COPY deploy/clamav/clamd.conf /etc/clamav/clamd.conf
