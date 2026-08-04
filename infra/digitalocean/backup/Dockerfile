FROM python:3.12-alpine

# postgresql16-client for pg_dump; awscli via pip (S3-compatible, works against DO
# Spaces with --endpoint-url) rather than the less-reliably-packaged apk aws-cli.
RUN apk add --no-cache postgresql16-client bash dcron \
	&& pip install --no-cache-dir --break-system-packages awscli

COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh

# Runs the backup once immediately, then every day at 04:00 UTC via cron in the
# foreground — keeps this a normal long-running compose service instead of relying
# on the host's own crontab.
ENTRYPOINT ["/bin/bash", "-c", "/usr/local/bin/backup.sh; echo '0 4 * * * /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1' | crontab - && crond -f -l 2"]
