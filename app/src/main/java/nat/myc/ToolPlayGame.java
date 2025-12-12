
package nat.myc;

import static androidx.test.InstrumentationRegistry.getContext;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RunWith(AndroidJUnit4.class)
public class ToolPlayGame {

    private static final String TAG            = "KTESTK_K";

    private static final String PKG_MAIN       = "com.devroq.cashhero";
    private static final String ACT_MAIN       = "com.devroq.cashhero.ui.main.MainActivity";

    private static final String PKG_GAME       = "com.devroq.chberryshoot";
    private static final String ACT_GAME       = "com.unity3d.player.UnityPlayerActivity";

    private static final long   LAUNCH_TIMEOUT = 25_000; // ms
    private static final long   FIND_TIMEOUT   = 20_000; // ms
    private static final long   SCROLL_PAUSE   = 600;    // ms



    static final long ACTIVE_MIN_MS = 35 * 60_000;  // 30 phút
    static final long ACTIVE_MAX_MS = 50 * 60_000;  // 40 phút
    static final long BREAK_MIN_MS  =  8 * 60_000;  // 4 phút
    static final long BREAK_MAX_MS  =  13 * 60_000;  // 5 phút




    static final float FORCE_STOP_RATE = 0.25f;

    static long nowMs() { return android.os.SystemClock.uptimeMillis(); }
    static long randBetween(long a, long b) {
        long span = Math.max(1, b - a + 1);
        return a + java.util.concurrent.ThreadLocalRandom.current().nextLong(span);
    }
    static boolean chance(float p) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() < Math.max(0, Math.min(1, p));
    }
    static final class SessionBreaker {
        private long nextCutoffAt = nowMs() + randBetween(ACTIVE_MIN_MS, ACTIVE_MAX_MS);

        // Chỉ về Home và đợi. KHÔNG force-stop, KHÔNG tự mở lại app.
        void maybeTakeBreak(UiDevice d) throws Exception {
            long now = nowMs();
            if (now < nextCutoffAt) return;

            long breakMs = randBetween(BREAK_MIN_MS, BREAK_MAX_MS);
            //Log.i("KTESTK", "⏸ Nghỉ " + (breakMs/1000) + "s (Home)…");

            try { d.pressHome(); } catch (Throwable t) { Log.w("KTESTK", "Home fail: " + t); }
            android.os.SystemClock.sleep(breakMs);

            // lên lịch ca tiếp theo
            nextCutoffAt = nowMs() + randBetween(ACTIVE_MIN_MS, ACTIVE_MAX_MS);
            throw new Exception("Nghỉ ngơi xong");
            //Log.i("KTESTK", "🔁 Hết nghỉ, tiếp tục. Ca kế tiếp trong ~" + ((nextCutoffAt - nowMs())/1000) + "s");
        }
    }

    private static void assertDemoNotExpired() {
        // Múi giờ VN
        TimeZone tz = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

        // Thời điểm hết hạn: 17/09/2025 00:00:00 +07
        Calendar expiry = Calendar.getInstance(tz);
        expiry.set(Calendar.YEAR, 2025);
        expiry.set(Calendar.MONTH, Calendar.NOVEMBER); // = 8
        expiry.set(Calendar.DAY_OF_MONTH, 1);          // sau ngày 16/9
        expiry.set(Calendar.HOUR_OF_DAY, 0);
        expiry.set(Calendar.MINUTE, 0);  // hoặc set(Calendar.MINUTE, 0)
        expiry.set(Calendar.SECOND, 0);
        expiry.set(Calendar.MILLISECOND, 0);

        long now = Calendar.getInstance(tz).getTimeInMillis();
        if (now >= expiry.getTimeInMillis()) {
            // Chọn 1 trong 2 cách:
            throw new RuntimeException("Đã bị F2P chặn. Không thể chạy");
            // hoặc: return; // nếu bạn muốn silently stop test
        }
    }
    /** Flow: mở mini-game → đợi Game view → (đợi 7s) → bấm Play (press-hold-release) → ném dao (có xử lý quảng cáo). */
    HandlePoint handlePoint = new HandlePoint();
    HandlePoint handlePoint_forWatchAds = new HandlePoint();
    Long startTime_playads = nowMs();
    int range_x = 60 * 1000;
    int range_y = 2*60*1000;
    Long durationTime = (long) getRandom(range_x,range_y);
    public void playerAds(Instrumentation ins, UiDevice d) throws Exception {
        //Log.d(TAG,"Đếm ngược: "+ (nowMs() - startTime_playads));
        if(nowMs() - startTime_playads < durationTime){
            return;
        }
        startTime_playads = nowMs();
        durationTime = (long) getRandom(range_x,range_y);
        if (!d.hasObject(By.pkg(PKG_MAIN).depth(0))) {
            Log.d(TAG, "App chính chưa foreground -> khởi động " + PKG_MAIN + "/" + ACT_MAIN);
            Intent intent = new Intent();
            intent.setClassName(PKG_MAIN, ACT_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ins.getContext().startActivity(intent);
            boolean ok = d.wait(Until.hasObject(By.pkg(PKG_MAIN).depth(0)), LAUNCH_TIMEOUT);
            Log.d(TAG, ok ? "Đã vào app chính." : "KHÔNG vào được app chính.");
        }
        swipeUp(d);

        BySelector watchVideoBtnSel = By.res(PKG_MAIN + ":id/btnAction").text("Watch ads");

        UiObject2 watchBtn = d.wait(Until.findObject(watchVideoBtnSel), 2000);
        if (watchBtn == null) {
            throw new Exception("Không xem quảng cáo được");
        }

        Rect r = watchBtn.getVisibleBounds();
        if (r.width() > 0 && r.height() > 0) {
            int x = r.centerX();
            int y = r.centerY();
            humanTap(getRandom(x-5,x+5),getRandom(y-5,y+5));
        }

        BySelector adWebViewSel = By.clazz("android.webkit.WebView");
        boolean adAppeared = d.wait(Until.hasObject(adWebViewSel), 2000);
        if (!adAppeared) {
            throw new Exception("Không xem quảng cáo được");
        }
        myTick=true;
        handleAds(d,125000);
        throw new Exception("Đã xử lí quảng cáo xong");
    }
    private Long duration_modeplay = (long) (10 * 60 * 1000);

    private Long duration_modeads = (long) (30 * 60 * 1000);
    private Long deadline_mode = nowMs() + duration_modeads;
    private int current_mode = 4;
    @Test
    public void runPlayGame() throws Exception {

//        assertDemoNotExpired();
        final SessionBreaker breaker = new SessionBreaker();
        while (true){
            try {
                var ins = InstrumentationRegistry.getInstrumentation();
                UiDevice d = UiDevice.getInstance(ins);





                Log.d(TAG, "Bắt đầu runPlayGame()");

                // Bảo đảm app chính foreground (khởi động nếu cần)
                if (!d.hasObject(By.pkg(PKG_MAIN).depth(0))) {
                    Log.d(TAG, "App chính chưa foreground -> khởi động " + PKG_MAIN + "/" + ACT_MAIN);
                    Intent intent = new Intent();
                    intent.setClassName(PKG_MAIN, ACT_MAIN);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    ins.getContext().startActivity(intent);
                    boolean ok = d.wait(Until.hasObject(By.pkg(PKG_MAIN).depth(0)), LAUNCH_TIMEOUT);
                    Log.d(TAG, ok ? "Đã vào app chính." : "KHÔNG vào được app chính.");
                } else {
                    Log.d(TAG, "App chính đã ở foreground.");
                }
                SystemClock.sleep(getRandom(6000,10000));
                Log.d(TAG, "Tìm nút btnPlay (thứ 2) bằng res + clazz, auto-scroll...");
                Log.d(TAG, "Đã bấm nút mở mini-game từ app chính.");
                pressAndRelease(getRandom(590,990), getRandom(1545,1720), /*size=*/0.01f, /*holdMs=*/60);

                // 2) Đợi mini-game mở: package foreground + Game view (content-desc="Game view")
                Log.d(TAG, "Chờ package mini-game foreground: " + PKG_GAME);
                if (!waitForPackage(d, PKG_GAME, LAUNCH_TIMEOUT)) {
                    Log.d(TAG, "Không thấy package game foreground. Kết thúc.");
                    throw new Exception("Không thấy package game foreground.");
//                return;
                }
                Log.d(TAG, "Package game đã foreground. Chờ Game view...");

                BySelector gameViewSel = By.desc("Game view");
                UiObject2 gameView = d.wait(Until.findObject(gameViewSel), FIND_TIMEOUT);
                if (gameView == null) {
                    Log.d(TAG, "Không thấy 'Game view' sau khi game mở. Kết thúc.");
                    return;
                }
                Log.d(TAG, "ĐÃ THẤY Game view.");

                // Đợi 7 giây theo yêu cầu trước khi bấm Play trong game
                Log.d(TAG, "Đợi 7 giây trước khi bấm Play theo toạ độ...");
                SystemClock.sleep(7_000);

                pressAndRelease(getRandom(302,775), getRandom(1274,1430), /*size=*/0.01f, /*holdMs=*/80);
                // Log.d(TAG, "Play trong game %=(" + rx + "," + ry + ") -> (" + realX + "," + realY + ") (press-hold-release)");

                // Chạm nhẹ Game view để chắc chắn focus input
                gameView = d.findObject(gameViewSel); // refresh
                if (gameView != null) {
                    Rect gv = gameView.getVisibleBounds();
                    pressAndRelease(getRandom(gv.centerX() - 200, gv.centerX() + 200), getRandom(gv.centerY() - 100, gv.centerY() + 100), /*size=*/0.01f, /*holdMs=*/60);
                    SystemClock.sleep(600);
                    Log.d(TAG, "Đã chạm Game view để lấy focus.");
                }

                int knives = 150;
                // Log.d(TAG, "Bắt đầu ném " + knives + " dao (press-hold-release), có xử lý quảng cáo...");
                for (int i = 0; i > -1; i++) {
//                    playerAds(ins,d);
                    breaker.maybeTakeBreak(d);

                    // Nếu Game view biến mất => có thể là overlay quảng cáo
                    if (!isGameViewVisible(d)) {
                        Log.d(TAG, "Game view biến mất trước dao #" + (i + 1) + " -> handle quảng cáo");
                        myTick = true;
                        handleAds(d, /*timeoutMs*/ 250_000); // tối đa 120s để thoát quảng cáo
                        // Sau khi xử lý, chờ Game view quay lại
                        boolean back = waitForGameView(d, FIND_TIMEOUT);
                        Log.d(TAG, back ? "Game view đã trở lại sau quảng cáo." : "KHÔNG thấy Game view sau khi xử lý quảng cáo.");
                        if (!back) break; // không còn game view -> thoát
                    }

                    // Lấy lại toạ độ theo màn hình hiện tại (trung tâm Game view)
                    UiObject2 gvNow = d.findObject(gameViewSel);
                    if (gvNow == null) {
                        Log.d(TAG, "Game view null ngay trước khi ném -> thử handle quảng cáo rồi thoát vòng.");
                        myTick=true;
                        handleAds(d, 250_000);
                        if (!waitForGameView(d, 8_000)) break;
                        gvNow = d.findObject(gameViewSel);
                        if (gvNow == null) break;
                    }
                    Rect r = gvNow.getVisibleBounds();
                    int centerX_bug = 278;

                    if (nowMs()  > deadline_mode) {
                        if(current_mode == 10){
                            current_mode = 4;
                            deadline_mode = nowMs() + getRandom((int) (duration_modeads-60 * 1000), (int) (duration_modeads+60 * 1000));
                        } else if(current_mode == 4){
                            current_mode = 10;
                            deadline_mode = nowMs() + getRandom((int) (duration_modeplay-60 * 1000), (int) (duration_modeplay+60 * 1000));
                        }

                    }
                    if (i % current_mode == 0) { //nhấn trong vùng play

                        pressAndRelease(getRandom(302,775), getRandom(1274,1430), randomFloat(0.04f, 0.09f), /*holdMs=*/getRandom(60, 210));

                    } else {
                        int x = getRandom(50, d.getDisplayWidth() - 50);
                        int y = getRandom((int) (d.getDisplayHeight() * 0.15), (int) (d.getDisplayHeight() * 0.5));
                        pressAndRelease(x, y, /*size=*/randomFloat(0.04f, 0.09f), /*holdMs=*/getRandom(60, 200));
                    }
                    int wait = ThreadLocalRandom.current().nextInt(250, 350); // ~0.95–1.22s giữa 2 lần ném
                    SystemClock.sleep(wait);
                }
                Log.d(TAG, "Kết thúc vòng ném dao (có xử lý quảng cáo).");
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            SystemClock.sleep(getRandom(2000, 4000));
        }
    }
    private int clickXYAndReturnIfNeeded(UiDevice d, int x, int y) throws Exception {
        Log.d(TAG, "clickXYAndReturnIfNeeded: (" + x + "," + y + ")");
//        pressAndRelease(x, y, /*size=*/0.01f, /*holdMs=*/80);
        humanTap(x,y);
        SystemClock.sleep(getRandom(500,700)); // chờ UI phản hồi

        if (!d.hasObject(By.pkg(PKG_GAME).depth(0))) {
            SystemClock.sleep(getRandom(1200,2000));
            Log.d(TAG, "clickXYAndReturnIfNeeded: đã rời game -> pressBack() tối đa 3 lần");
            for (int i = 0; i < 3; i++) {
                d.pressBack();
                SystemClock.sleep(getRandom(500,1000));
                if (d.hasObject(By.pkg(PKG_GAME).depth(0))) {
                    Log.d(TAG, "clickXYAndReturnIfNeeded: quay lại game sau back #" + (i + 1));
                    return 1;
                }
            }
        }
        return 0;
    }
    boolean myTick = false;
    public class Point  {
        public int x;
        public int y;
        public int score;
        Point(int x, int y){
            this.x = x;
            this.y = y;
            this.score = 0;
        }
        Point(){
            this.x = 0;
            this.y = 0;
            this.score = 0;
        }
    }
    public class HandlePoint{
        private List<Point> pointsResults = new ArrayList<>();
        HandlePoint(){

        }

        public boolean loopRightPoint(UiDevice d) throws Exception {
            int i = 0;
            for (Point p : pointsResults) {
                int x = getRandom(p.x - 5, p.x + 5);
                int y = getRandom(p.y - 5,p.y + 5);
                Log.d(TAG,"Nhấn nút "+ i++ +" trong cache : "+x+","+y);
                clickXYAndReturnIfNeeded(d, x, y);
                if(!isGameViewVisible(d)){
                    d.pressBack();
                    SystemClock.sleep(getRandom(300,600));
                } else {
                    return true;
                }
            }
            return false;
        }


        public boolean addPointRight(int x, int y) {
            long bestD2 = Long.MAX_VALUE;
            Point best = null;
            for (Point p : pointsResults) {
                long dx = (long) x - p.x;
                long dy = (long) y - p.y;
                long d2 = dx * dx + dy * dy;
                if (d2 < bestD2 && d2 <= 48*48) {
                    bestD2 = d2;
                    best = p;
                }
            }

            // 2) Nếu có điểm đủ gần -> gộp
            if (best != null) {
                best.x = (int) Math.round((best.x + x) / 2.0);
                best.y = (int) Math.round((best.y + y) / 2.0);
                return true;
            }

            // 3) Không có điểm nào gần:
            if (pointsResults.size() < 5) {
                pointsResults.add(new Point(x, y));
            } else {
                // ĐÃ ĐỦ 6: thay thế theo một policy đơn giản.
                // Ví dụ: thay phần tử cuối (hoặc điểm có score thấp nhất nếu bạn có 'score').
                pointsResults.set(pointsResults.size() - 1, new Point(x, y));
            }
            return false;
        }
    }
    private static boolean isLikelyCloseButton(Rect br, UiDevice device) {
        if (br == null) return false;

        int w = device.getDisplayWidth();
        int h = device.getDisplayHeight();

        // 1) Ở vùng phía trên màn hình (top band)
        boolean inTopBand = br.centerY() < (h * 0.20);

        // 2) Nằm ở góc trái hoặc góc phải
        boolean inLeftCorner  = br.centerX() <= (w * 0.20);
        boolean inRightCorner = br.centerX() >= (w * 0.80);

        // 3) Kích thước nhỏ (tránh các ảnh lớn ở giữa)
        boolean small = br.width() <= (w * 0.20) && br.height() <= (h * 0.1);

        // 4) Không quá sát mép trên (tránh gesture bar/edge)
        int edgePad = 0;
        boolean notTooEdge = br.top > edgePad && br.left > edgePad && br.right < (w - edgePad);


        return inTopBand && (inLeftCorner || inRightCorner) && small && notTooEdge;
    }
    private List<android.graphics.Rect> findByText(UiDevice device, List<String> texts){
        List<android.graphics.Rect> res = new ArrayList<>();
        for (String t : texts) {
            UiObject2 obj = device.findObject(By.text(t));

            if (obj != null) {
                // Lấy bounding rectangle của element
                android.graphics.Rect rect = obj.getVisibleBounds();
                res.add(rect);
            }
        }
        return res;
    }
    private List<Rect> findAdImageBounds(UiDevice device) {
        // Pattern match nhiều class (kể cả AppCompat*)
        Pattern classes = Pattern.compile(".*(Image(View)?|ImageButton|Button|View|Image|ImageView|TextView)$");

        // Chờ 2 giây cho UI ổn định
        device.waitForIdle(2000);

        Set<String> dedup = new LinkedHashSet<>();
        List<Rect> rects = new ArrayList<>();

        // Lấy tất cả node match pattern
        List<UiObject2> nodes = device.findObjects(By.clazz(classes));

        for (UiObject2 n : nodes) {
            Rect b = n.getVisibleBounds();
            if (b.width() > 0 && b.height() > 0) {
                String key = b.left + "," + b.top + "," + b.right + "," + b.bottom;
                if (dedup.add(key)) {
                    rects.add(new Rect(b));
                }
            }
        }

        return rects;
    }

    public static boolean takeSafeScreenshot(@NonNull UiDevice d, @NonNull Context context, @NonNull String filename) {
        // Lấy thư mục cache ngoài của app
        File dir = context.getExternalCacheDir();
        if (dir == null) {
            Log.e(TAG, "External cache dir is null! Không thể chụp màn hình.");
            return false;
        }

        // Tạo thư mục nếu chưa tồn tại
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Không tạo được thư mục cache: " + dir.getAbsolutePath());
            return false;
        }

        // Tạo file screenshot
        File screenshot = new File(dir, filename);
        Log.d(TAG, "Screenshot path: " + screenshot.getAbsolutePath());

        // Chụp màn hình
        boolean success = d.takeScreenshot(screenshot);
        if (!success) {
            Log.e(TAG, "Chụp màn hình thất bại");
        } else {
            Log.d(TAG, "Chụp màn hình thành công");
        }

        return success;
    }
    private void handleAds(UiDevice d, long timeoutMs) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;

        // ========= LẦN ĐẦU =========
        if (myTick) {
            Log.d(TAG, "Đợi lần đầu");
            SystemClock.sleep(getRandom(15000, 20000)); // giảm còn 30–40s
            Log.d(TAG, "Đợi xong");
            d.pressBack();
            myTick = false;
        }

        Log.d(TAG, "Bắt đầu lặp");

        // ========= VÒNG LẶP CHÍNH =========
        while (SystemClock.uptimeMillis() < deadline) {

            // Nếu đã quay lại màn game => kết thúc
            if (isGameViewVisible(d)) {
                return;
            }

            Log.d(TAG, "Chụp ảnh và gửi về server...");
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            File dir = context.getExternalCacheDir();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();  // tạo thư mục nếu chưa tồn tại
            }

            File screenshot = new File(dir, "screenshot.png");
            boolean success = d.takeScreenshot(screenshot);
            if (!success) {
                Log.e(TAG, "Chụp màn hình thất bại");
            }
            Log.d(TAG, "Screenshot path: " + screenshot.getAbsolutePath());
            // 2. Gửi ảnh đến API server
            List<Rect> boxes = callApiDetectBoundingBox(screenshot);

            Log.d(TAG, "Số lượng bounding box nhận từ server: " + boxes.size());

            // 3. Nếu server trả về các box → bấm thử từng box
            int idx = 0;
            for (Rect r : boxes) {

                int x = r.centerX();
                int y = r.centerY();
                int xx = getRandom(x - 5, x + 5);
                int yy = getRandom(y - 5, y + 5);

                Log.d(TAG, "Tap box " + idx++ + " tại: (" + xx + "," + yy + ")");
                clickXYAndReturnIfNeeded(d, xx, yy);

                SystemClock.sleep(getRandom(1500, 2500));

                if (isGameViewVisible(d)) {
                    Log.d(TAG,"Lưu vào mảng điểm: ("+xx+","+yy+")"); handlePoint.addPointRight(xx,yy);
                    Log.d(TAG, "Đã quay lại game → return");
                    return;
                } else {
                    d.pressBack();
                }

            }
            if (!isGameViewVisible(d)) {
                handlePoint.loopRightPoint(d);
            }
            List<String> texts = new ArrayList<>(Arrays.asList("See next","Stay and continue"));
            List<android.graphics.Rect> findText = findByText(d, texts);
            for (android.graphics.Rect r : findText){
                clickXYAndReturnIfNeeded(d, getRandom(r.centerX()-5,r.centerX()+5), getRandom(r.centerY()-5,r.centerY()+5));
                if (isGameViewVisible(d)) {
                    return;
                }
            }
            Log.d(TAG, "Không quay lại game → đợi 10s rồi lặp lại");
            SystemClock.sleep(5000); // Mỗi 10 giây chụp 1 lần
        }

        // ========= HẾT THỜI GIAN =========
        if (!isGameViewVisible(d)) {
            throw new Exception("Hết thời gian chờ quảng cáo.");
        }
    }

    private List<Rect> callApiDetectBoundingBox(File imageFile) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)   // Tăng vì xử lý SIFT có thể chậm
                .writeTimeout(60, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))  // Tắt reuse
                .protocols(java.util.Arrays.asList(Protocol.HTTP_1_1))
                .build();

        RequestBody fileBody = RequestBody.create(imageFile, MediaType.parse("image/*"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url("http://192.168.0.112:8000/detect")
                .post(requestBody)
                .addHeader("Connection", "close")
                .build();

        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("HTTP " + response.code() + ": " + response.message());
                }

                String json = response.body().string();

                JSONObject obj = new JSONObject(json);
                JSONArray arr = obj.getJSONArray("boxes");
                List<Rect> result = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONArray box = arr.getJSONArray(i);
                    int x = box.getInt(0);
                    int y = box.getInt(1);
                    int w = box.getInt(2);
                    int h = box.getInt(3);
                    result.add(new Rect(x, y, x + w, y + h));
                }
                return result;
            } catch (IOException e) {
                if (retry == maxRetries - 1) {
                    throw new Exception("Lỗi network sau " + maxRetries + " lần thử: " + e.getMessage(), e);
                }
                // Delay nhẹ trước khi retry
                Thread.sleep(500);
            }
        }
        throw new Exception("Không thể kết nối API");
    }
    private static int randBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }


    public int getRandom(int x_min, int x_max){
        Random random = new Random();
        return random.nextInt(x_max - x_min + 1) + x_min;
    }

    private boolean isGameViewVisible(UiDevice d) {
        final long TIMEOUT_MS = 1500; // 3 giây, bạn chỉnh theo nhu cầu
        try {
            return d.wait(Until.hasObject(By.desc("Game view")), TIMEOUT_MS);
        } catch (Exception e) {
//            Log.w(TAG, "isGameViewVisible: error = " + e);
            return false;
        }
    }
    private boolean waitForGameView(UiDevice d, long timeoutMs) {
        return d.wait(Until.hasObject(By.desc("Game view")), timeoutMs);
    }

    // =========================== Helpers (tìm nút play, swipe, package) ===========================

    /**
     * Tìm nút thứ 2:
     *  - class = android.widget.Button
     *  - resource-id = com.skyoutlet.F2P:id/btnPlay
     * Auto-scroll (swipe up) tối đa maxScrolls lần nếu chưa thấy.
     */
    private UiObject2 findSecondPlayButtonWithScroll(UiDevice d, int maxScrolls) {
        BySelector sel = By.clazz("android.widget.Button").res(PKG_MAIN + ":id/btnPlay");
        swipeUp(d);
        SystemClock.sleep(SCROLL_PAUSE);
        // Thử tìm ngay
        List<UiObject2> list = d.findObjects(sel);
        Log.d(TAG, "findObjects(btnPlay) ngay lập tức: size=" + (list == null ? 0 : list.size()));
        if (list != null && list.size() >= 2) return list.get(1);

        // Không thấy => scroll và tìm lại
        for (int i = 0; i < maxScrolls; i++) {
            swipeUp(d);
            SystemClock.sleep(SCROLL_PAUSE);
            list = d.findObjects(sel);
            Log.d(TAG, "Sau scroll #" + (i + 1) + ", size=" + (list == null ? 0 : list.size()));
            if (list != null && list.size() >= 2) return list.get(1);
        }

        return null;
    }

    /** Swipe up từ ~80%H -> ~30%H. */
    private void swipeUp(UiDevice d) {
        int w = d.getDisplayWidth();
        int h = d.getDisplayHeight();
        int startX = w / 2;
        int startY = (int) (h * 0.80);
        int endX   = w / 2;
        int endY   = (int) (h * 0.30);
        Log.d(TAG, "Swipe up: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ")");
        d.swipe(startX, startY, endX, endY, /*steps*/18);
    }
    /** Swipe down từ ~30%H -> ~80%H. */
    private void swipeDown(UiDevice d) {
        int w = d.getDisplayWidth();
        int h = d.getDisplayHeight();
        d.swipe(w/2, (int)(h*0.30), w/2, (int)(h*0.80), 18);
    }

    /** Chờ package foreground (heuristic đủ dùng cho Unity). */
    private boolean waitForPackage(UiDevice d, String pkg, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        int poll = 0;
        while (SystemClock.uptimeMillis() < end) {
            boolean onPkg = d.hasObject(By.pkg(pkg).depth(0));
            Log.d(TAG, "Đợi package '" + pkg + "' foreground... poll=" + (poll++) + " -> " + onPkg);
            if (onPkg) return true;
            SystemClock.sleep(350);
        }
        return false;
    }

    // =========================== Low-level touch: press / release / combined ===========================

    /** Tạo MotionEvent chung. */
    private static MotionEvent buildMotionEvent(int action, long downTime,
                                                float x, float y,
                                                float pressure, float size) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
        props[0] = pp;

        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x;
        pc.y = y;
        pc.pressure = pressure;
        pc.size = size;
        pc.orientation = 0f;
        coords[0] = pc;

        long eventTime = SystemClock.uptimeMillis();

        return MotionEvent.obtain(
                downTime, eventTime, action,
                /*pointerCount*/1, props, coords,
                /*metaState*/0, /*buttonState*/0,
                /*xPrecision*/1f, /*yPrecision*/1f,
                /*deviceId*/0, /*edgeFlags*/0,
                InputDevice.SOURCE_TOUCHSCREEN, /*flags*/0
        );
    }
    private static float dpToPx(float dp) {
        float density = androidx.test.core.app.ApplicationProvider
                .getApplicationContext().getResources().getDisplayMetrics().density;
        return dp * density;
    }
    private static float randomFloat(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    public static boolean humanTap(float x, float y) {
        try {
            Instrumentation inst = InstrumentationRegistry.getInstrumentation();
            var ui = inst.getUiAutomation();

            // ---- 1) Random hoá vị trí trong bán kính 1–3dp
            float jitterDp =  randomFloat(1f, 3.1f);
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            float dx = (float)(Math.cos(angle) * dpToPx(jitterDp));
            float dy = (float)(Math.sin(angle) * dpToPx(jitterDp));
            float x0 = x + dx;
            float y0 = y + dy;

            // ---- 2) Các tham số “giống người”
            int holdMs   = ThreadLocalRandom.current().nextInt(60, 161);   // 60–160ms
            int preMs    = ThreadLocalRandom.current().nextInt(6, 15);     // DOWN→MOVE
            int postMs   = ThreadLocalRandom.current().nextInt(150, 351);  // sau UP chờ UI
            float size   = randomFloat(0.05f, 0.12f);
            float slopPx = ViewConfiguration.get(inst.getTargetContext()).getScaledTouchSlop();
            float microMove = Math.min(dpToPx(0.5f) + ThreadLocalRandom.current().nextFloat() * dpToPx(0.5f),
                    Math.max(1f, slopPx * 0.3f)); // < touch slop

            long downTime = SystemClock.uptimeMillis();

            // ---- 3) ACTION_DOWN (áp lực tăng nhẹ)
            MotionEvent evDown = obtainTouch(
                    MotionEvent.ACTION_DOWN, downTime, SystemClock.uptimeMillis(),
                    x0, y0, /*pressure=*/0.6f, size
            );
            ui.injectInputEvent(evDown, true);
            evDown.recycle();

            // ---- 4) (tuỳ chọn) 1 MOVE rất nhỏ sau preMs
            SystemClock.sleep(preMs);
            float x1 = x0 + (ThreadLocalRandom.current().nextBoolean() ? microMove : -microMove);
            float y1 = y0 + (ThreadLocalRandom.current().nextBoolean() ? microMove : -microMove);
            MotionEvent evMove = obtainTouch(
                    MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis(),
                    x1, y1, /*pressure=*/0.7f, size
            );
            ui.injectInputEvent(evMove, true);
            evMove.recycle();

            // ---- 5) Giữ một khoảng tự nhiên
            SystemClock.sleep(holdMs);

            // ---- 6) ACTION_UP (áp lực về 0)
            MotionEvent evUp = obtainTouch(
                    MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(),
                    x1, y1, /*pressure=*/0f, size
            );
            ui.injectInputEvent(evUp, true);
            evUp.recycle();

            // ---- 7) Đợi UI phản hồi tự nhiên
            SystemClock.sleep(postMs);
            return true;
        } catch (Exception e) {
            // Log nếu cần
            return false;
        }
    }
    private static MotionEvent obtainTouch(int action, long downTime, long eventTime,
                                           float x, float y, float pressure, float size) {
        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties p0 = new MotionEvent.PointerProperties();
        p0.id = 0;
        p0.toolType = MotionEvent.TOOL_TYPE_FINGER;
        pp[0] = p0;

        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords c0 = new MotionEvent.PointerCoords();
        c0.x = x;
        c0.y = y;
        c0.pressure = pressure;
        c0.size = size;
        pc[0] = c0;

        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,          // pointerCount
                pp,
                pc,
                0,          // metaState
                0,          // buttonState
                1.0f,       // xPrecision
                1.0f,       // yPrecision
                0,          // deviceId
                0,          // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN, // source
                0           // flags
        );
    }
    /** Gửi ACTION_DOWN (nhấn). Trả về downTime để dùng cho ACTION_UP. */
    private static long pressDown(float x, float y, float size) throws Exception {
        var ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = buildMotionEvent(MotionEvent.ACTION_DOWN, downTime, x, y, /*pressure=*/0.8f, size);
//        Log.d(TAG, "pressDown tại (" + x + "," + y + "), size=" + size + ", downTime=" + downTime);
        try {
            ui.injectInputEvent(down, true);
        } finally {
            down.recycle();
        }
        return downTime;
    }

    /** Gửi ACTION_UP (thả), sử dụng cùng downTime với lần nhấn. */
    private static void releaseUp(float x, float y, float size, long downTime) throws Exception {
        var ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        MotionEvent up = buildMotionEvent(MotionEvent.ACTION_UP, downTime, x, y, /*pressure=*/0.0f, size);
//        Log.d(TAG, "releaseUp tại (" + x + "," + y + "), size=" + size + ", downTime=" + downTime);
        try {
            ui.injectInputEvent(up, true);
        } finally {
            up.recycle();
        }
    }

    /** Nhấn-giữ-thả như người. */
    private static void pressAndRelease(float x, float y, float size, int holdMs) throws Exception {
        long t0 = pressDown(x, y, size);
        SystemClock.sleep(Math.max(holdMs, 25)); // giữ 1 chút
        releaseUp(x, y, size, t0);
    }

    @SuppressWarnings("unused")
    private static int dp(float dps) {
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        return (int) (dps * dm.density + 0.5f);
    }
}

