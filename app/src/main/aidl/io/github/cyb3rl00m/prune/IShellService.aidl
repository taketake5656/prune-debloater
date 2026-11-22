package io.github.cyb3rl00m.prune;

import android.os.Bundle;

// Shizuku のプロセス内で shell (または root) ユーザーとして動く。
interface IShellService {
    // サービスのバインドが解除されたときに Shizuku が呼ぶ。トランザクションコードは Shizuku が固定している。
    void destroy() = 16777114;

    // 指定された argv でプログラムを 1 つ実行する (シェルは使わない)。exitCode (int)、stdout、stderr (String) を返す。
    Bundle exec(in String[] argv, long timeoutMillis) = 1;

    // サービスが動く uid: 2000 = shell (ADB/Shizuku)、0 = root。
    int uid() = 2;
}
