package ru.replyai.android;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UserInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class SshInstaller implements AutoCloseable {
    private volatile boolean closed;
    private volatile Session session;
    private final SocketFactory socketFactory;

    public static final class Spec {
        public final String domain;
        public final String ip;
        public final int port;

        public Spec(String str, String str2, String str3) throws Exception {
            int i;
            int i2;
            String strTrim = str.trim();
            this.ip = strTrim;
            String[] strArrSplit = strTrim.split("\\.", -1);
            if (strArrSplit.length != 4) {
                throw new Exception("Введите публичный IPv4 сервера, без http и пробелов.");
            }
            int[] iArr = new int[4];
            for (int i3 = 0; i3 < 4; i3++) {
                if (!strArrSplit[i3].matches("0|[1-9][0-9]{0,2}")) {
                    throw new Exception("Проверьте IPv4 сервера.");
                }
                int i4 = Integer.parseInt(strArrSplit[i3]);
                iArr[i3] = i4;
                if (i4 > 255) {
                    throw new Exception("Проверьте IPv4 сервера.");
                }
            }
            int i5 = iArr[0];
            if (i5 == 0 || i5 == 10 || i5 == 127 || i5 >= 224 || ((i5 == 169 && iArr[1] == 254) || ((i5 == 192 && iArr[1] == 168) || ((i5 == 172 && (i2 = iArr[1]) >= 16 && i2 <= 31) || (i5 == 100 && (i = iArr[1]) >= 64 && i <= 127))))) {
                throw new Exception("Нужен публичный IPv4 VPS. Локальный адрес или адрес за NAT не подходит.");
            }
            try {
                int i6 = Integer.parseInt(str2.trim());
                this.port = i6;
                if (i6 < 1 || i6 > 65535) {
                    throw new Exception("SSH-порт должен быть от 1 до 65535.");
                }
                String lowerCase = str3.trim().toLowerCase(Locale.ROOT);
                lowerCase = lowerCase.isEmpty() ? this.ip.replace('.', '-') + ".sslip.io" : lowerCase;
                if (lowerCase.length() > 253 || !lowerCase.matches("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
                    throw new Exception("Введите DNS-имя без https, пути и пробелов либо оставьте поле пустым.");
                }
                this.domain = lowerCase;
            } catch (Exception unused) {
                throw new Exception("Проверьте SSH-порт, обычно 22.");
            }
        }

        public String url() {
            return "https://" + this.domain;
        }

        public String identity() {
            return this.ip + ":" + this.port;
        }
    }

    public static final class TrustRequired extends Exception {
        public final String fingerprint;

        TrustRequired(String str) {
            super("Подтвердите SSH-ключ сервера.");
            this.fingerprint = str;
        }
    }

    public SshInstaller() {
        this(null);
    }

    SshInstaller(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    public void connect(Spec spec, byte[] bArr, final String str) throws Exception {
        if (this.closed || Thread.currentThread().isInterrupted()) {
            Arrays.fill(bArr, (byte) 0);
            throw new InterruptedException();
        }
        JSch jSch = new JSch();
        final String[] strArr = {null};
        jSch.setHostKeyRepository(new HostKeyRepository() { // from class: ru.replyai.android.SshInstaller.1
            @Override // com.jcraft.jsch.HostKeyRepository
            public void add(HostKey hostKey, UserInfo userInfo) {
            }

            @Override // com.jcraft.jsch.HostKeyRepository
            public void remove(String str2, String str3) {
            }

            @Override // com.jcraft.jsch.HostKeyRepository
            public void remove(String str2, String str3, byte[] bArr2) {
            }

            @Override // com.jcraft.jsch.HostKeyRepository
            public int check(String str2, byte[] bArr2) {
                strArr[0] = SshInstaller.fingerprint(bArr2);
                String str3 = str;
                if (str3 == null || str3.isEmpty()) {
                    return 1;
                }
                return str.equals(strArr[0]) ? 0 : 2;
            }

            @Override // com.jcraft.jsch.HostKeyRepository
            public String getKnownHostsRepositoryID() {
                return "Reply AI pinned host keys";
            }

            @Override // com.jcraft.jsch.HostKeyRepository
            public HostKey[] getHostKey() {
                return new HostKey[0];
            }

            @Override // com.jcraft.jsch.HostKeyRepository
            public HostKey[] getHostKey(String str2, String str3) {
                return new HostKey[0];
            }
        });
        this.session = jSch.getSession("root", spec.ip, spec.port);
        if (this.socketFactory != null) {
            this.session.setSocketFactory(this.socketFactory);
        }
        this.session.setConfig("StrictHostKeyChecking", "yes");
        this.session.setConfig("PreferredAuthentications", "password");
        this.session.setConfig("server_host_key", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,rsa-sha2-512,rsa-sha2-256");
        this.session.setConfig("kex", "ecdh-sha2-nistp256,ecdh-sha2-nistp384,diffie-hellman-group14-sha256");
        this.session.setConfig("cipher.c2s", "aes128-ctr,aes256-ctr");
        this.session.setConfig("cipher.s2c", "aes128-ctr,aes256-ctr");
        this.session.setConfig("mac.c2s", "hmac-sha2-256,hmac-sha2-512");
        this.session.setConfig("mac.s2c", "hmac-sha2-256,hmac-sha2-512");
        this.session.setPassword(bArr);
        this.session.setServerAliveInterval(10000);
        this.session.setServerAliveCountMax(3);
        try {
            try {
                if (this.closed || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                this.session.connect(AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH);
                if (this.closed || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                Arrays.fill(bArr, (byte) 0);
                if (this.session != null) {
                    this.session.setPassword(new byte[0]);
                }
            } catch (Exception e) {
                close();
                if (strArr[0] != null && (str == null || str.isEmpty())) {
                    throw new TrustRequired(strArr[0]);
                }
                String str2 = strArr[0];
                if (str2 != null && !str2.equals(str)) {
                    throw new Exception("SSH-ключ сервера изменился. Проверьте VPS в кабинете хостинга. Пароль не отправлен.");
                }
                throw new Exception("Не удалось войти по SSH. Проверьте запуск VPS, IPv4, SSH-порт и root-пароль. Если парольный вход запрещён, включите его в кабинете провайдера.", e);
            }
        } catch (Throwable th) {
            Arrays.fill(bArr, (byte) 0);
            if (this.session != null) {
                this.session.setPassword(new byte[0]);
            }
            throw th;
        }
    }

    public void preflight() throws Exception {
        String strCommand = command("id -u; . /etc/os-release; printf '%s:%s\\n' \"$ID\" \"$VERSION_ID\"; test -d /run/systemd/system; command -v systemd-run >/dev/null");
        if (!strCommand.equals("0\ndebian:12\n") && !strCommand.equals("0\nubuntu:24.04\n")) {
            throw new Exception("Для установки нужны root, systemd и Debian 12 либо Ubuntu 24.04. Проверьте выбранную ОС.");
        }
    }

    public static String directory(String str) {
        if (str == null || !str.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Некорректная установка.");
        }
        return "/root/replyai-install-" + str;
    }

    public void begin(Spec spec, String str, InputStream inputStream) throws Exception {
        String strDirectory = directory(str);
        command("install -d -m 0700 " + strDirectory);
        ChannelSftp channelSftp = (ChannelSftp) this.session.openChannel("sftp");
        try {
            channelSftp.connect(15000);
            channelSftp.put(inputStream, strDirectory + "/server.tar.gz");
            channelSftp.chmod(384, strDirectory + "/server.tar.gz");
            channelSftp.disconnect();
            command("tar -xzf " + strDirectory + "/server.tar.gz -C " + strDirectory + " --no-same-owner && systemd-run --quiet --collect --no-block --unit=replyai-setup-" + str + " --property=RuntimeMaxSec=1800 /bin/bash " + strDirectory + "/tools/bootstrap.sh '" + spec.domain + "' " + strDirectory);
        } catch (Throwable th) {
            channelSftp.disconnect();
            throw th;
        }
    }

    public String state(String str) throws Exception {
        String strCommand = command("s=$(cat " + directory(str) + "/status 2>/dev/null || true); case \"$s\" in DONE|HTTPS_PENDING|FAILED|BUSY) printf '%s' \"$s\" ;; *) a=$(systemctl show -p ActiveState --value " + ("replyai-setup-" + str) + " 2>/dev/null || true); case \"$a\" in active|activating|reloading) printf '%s' \"${s:-QUEUED}\" ;; *) printf MISSING ;; esac ;; esac");
        if (strCommand.matches("DONE|HTTPS_PENDING|FAILED|BUSY|QUEUED|INSTALLING|CHECKING|HTTPS|MISSING")) {
            return strCommand;
        }
        throw new Exception("Не удалось определить этап установки. Повторите подключение.");
    }

    public String token() throws Exception {
        String strTrim = command("sed -n 's/^REPLYAI_ADMIN_TOKEN=//p' /etc/replyai/server.env").trim();
        RemoteClient.validateToken(strTrim);
        return strTrim;
    }

    public JSONObject diagnostics(String str) throws Exception {
        if (!str.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException();
        }
        String str2 = "/root/replyai-install-" + str;
        return new JSONObject(command("python3 " + str2 + "/tools/diagnose.py " + str2));
    }

    String command(String str) throws Exception {
        int i;
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        if (this.session == null || !this.session.isConnected()) {
            throw new Exception("Соединение SSH закрыто. Повторите подключение.");
        }
        ChannelExec channelExec = (ChannelExec) this.session.openChannel("exec");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        channelExec.setCommand(str);
        channelExec.setInputStream(null);
        channelExec.setErrStream(new OutputStream() { // from class: ru.replyai.android.SshInstaller.2
            @Override // java.io.OutputStream
            public void write(int i2) {
            }
        });
        try {
            InputStream inputStream = channelExec.getInputStream();
            try {
                channelExec.connect(15000);
                long jCurrentTimeMillis = System.currentTimeMillis() + 30000;
                byte[] bArr = new byte[4096];
                while (!Thread.currentThread().isInterrupted()) {
                    while (inputStream.available() > 0 && (i = inputStream.read(bArr)) >= 0) {
                        if (byteArrayOutputStream.size() + i > 32768) {
                            throw new Exception("Слишком большой ответ SSH.");
                        }
                        byteArrayOutputStream.write(bArr, 0, i);
                    }
                    if (channelExec.isClosed() && inputStream.available() == 0) {
                        if (channelExec.getExitStatus() != 0) {
                            throw new Exception("Команда настройки VPS завершилась с ошибкой. Проверьте Debian 12, root-доступ и состояние сервера.");
                        }
                        String string = byteArrayOutputStream.toString("UTF-8");
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        channelExec.disconnect();
                        return string;
                    }
                    if (System.currentTimeMillis() > jCurrentTimeMillis) {
                        throw new Exception("VPS слишком долго отвечает по SSH. Повторите подключение.");
                    }
                    Thread.sleep(40L);
                }
                throw new InterruptedException();
            } catch (Throwable th) {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        } catch (Throwable th3) {
            channelExec.disconnect();
            throw th3;
        }
    }

    static String fingerprint(byte[] bArr) {
        try {
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bArr));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override // java.lang.AutoCloseable
    public void close() {
        this.closed = true;
        Session session = this.session;
        if (session != null) {
            session.disconnect();
        }
    }
}
