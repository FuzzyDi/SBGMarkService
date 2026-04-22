package uz.sbg.kmreplacement.lifecycle;

import org.junit.Before;
import org.junit.Test;
import uz.sbg.kmreplacement.config.KmReplacementConfig;
import uz.sbg.kmreplacement.resolver.ReplacementResolver;
import uz.sbg.kmreplacement.resolver.ResolveContext;
import uz.sbg.kmreplacement.resolver.ResolveOutcome;
import uz.sbg.kmreplacement.state.CorrelationKey;
import uz.sbg.kmreplacement.state.InMemoryReplacementStateRepository;
import uz.sbg.kmreplacement.state.ReplacementState;
import uz.sbg.kmreplacement.state.ReplacementStateRepository;
import uz.sbg.kmreplacement.state.Status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Тесты чистой логики StateMachine: без Swing, без HTTP — только переходы.
 *
 * <p>Используем заглушку {@link ScriptedResolver}, которая возвращает
 * заранее заданный ответ. Это даёт полный контроль над сценарием.</p>
 */
public class StateMachineTest {

    private static final int SHOP = 101;
    private static final int POS  = 4;
    private static final int RECEIPT_NUM = 1000;
    private static final String RECEIPT = String.valueOf(RECEIPT_NUM);
    private static final String GTIN = "04780069000130";
    private static final String KM_A = "01" + GTIN + "21SCANNED_BY_CASHIER";
    private static final String KM_B = "01" + GTIN + "21REPLACEMENT_FROM_POOL";

    private ReplacementStateRepository repo;
    private ScriptedResolver resolver;
    private FakeClock clock;
    private KmReplacementConfig config;
    private StateMachine sm;

    @Before
    public void setUp() {
        repo = new InMemoryReplacementStateRepository();
        resolver = new ScriptedResolver();
        clock = new FakeClock(10_000L);
        config = new KmReplacementConfig("http://x", 3000, 5000, 60_000, 2);
        sm = new StateMachine(resolver, repo, config, clock);
    }

    // ---------------------------------------------------------------
    // Happy path: A невалиден → QR_SHOWN; скан B → ACCEPT_CLOSE_OVERLAY.
    // ---------------------------------------------------------------
    @Test
    public void happyPath_rejectThenReplacementAccepted() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        // 1. A невалиден, предлагается B
        resolver.next(ResolveOutcome.replaceWith(KM_B));
        Decision d1 = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d1.getKind());
        assertEquals(KM_B, d1.getReplacementKm());
        assertTrue(d1.shouldShowOverlay());

        ReplacementState st = repo.find(key);
        assertNotNull(st);
        assertEquals(Status.QR_SHOWN, st.getStatus());
        assertEquals(KM_A, st.getOriginalKm());
        assertEquals(KM_B, st.getReplacementKm());
        assertEquals(1, st.getAttemptCount());

        // 2. Кассир сканирует QR → это теперь B, и B валиден
        resolver.next(ResolveOutcome.valid());
        Decision d2 = sm.onScan(KM_B, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT_CLOSE_OVERLAY, d2.getKind());
        assertTrue(d2.shouldCloseOverlay());

        st = repo.find(key);
        assertEquals(Status.REPLACEMENT_ACCEPTED, st.getStatus());
    }

    // ---------------------------------------------------------------
    // КМ сразу валиден → ACCEPT без overlay.
    // ---------------------------------------------------------------
    @Test
    public void validKm_acceptNoOverlay() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.next(ResolveOutcome.valid());
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT, d.getKind());
        assertNull(repo.find(key));
    }

    // ---------------------------------------------------------------
    // TTL: overlay истёк, повторный скан A → новая замена (свежая запись).
    // ---------------------------------------------------------------
    @Test
    public void expiredState_allowsNewReplacementCycle() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        resolver.next(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(Status.QR_SHOWN, repo.find(key).getStatus());

        // сдвигаем время за TTL
        clock.advance(61_000L);

        resolver.next(ResolveOutcome.replaceWith(KM_B));
        Decision d = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertTrue(d.shouldShowOverlay());
        // запись пересоздана с attemptCount=1 (после перевода в EXPIRED прежняя не учитывается)
        assertEquals(1, repo.find(key).getAttemptCount());
    }

    // ---------------------------------------------------------------
    // Anti-cycle: maxAttempts=2. После 2-й попытки — FAILED, без overlay.
    // ---------------------------------------------------------------
    @Test
    public void maxAttemptsExceeded_returnsFailedReject() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        // 1-я попытка: создаётся QR_SHOWN, attempts=1
        resolver.next(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        // Симулируем ситуацию "кассир не отсканировал QR, а снова сканирует A":
        // overlay всё ещё QR_SHOWN → state machine возвращает REJECT + "Отсканируйте B".
        // Но нам важно проверить ветку maxAttempts — для этого нужно сначала,
        // чтобы QR_SHOWN выродился (EXPIRED). Сдвигаем время.
        clock.advance(61_000L);
        resolver.next(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, key, ctx, RECEIPT_NUM);   // attemptCount=1 (после EXPIRED — свежий старт)
        // Проэмулируем зафиксированный FAILED путь: на следующей попытке сценарий
        // где уже attemptCount=2 — принудительно ставим его.
        ReplacementState st = repo.find(key);
        st.incrementAttemptCount();   // attempts=2
        repo.save(st);

        // Ещё один скан при attemptCount>=maxAttempts=2 → FAILED
        resolver.next(ResolveOutcome.replaceWith(KM_B));
        Decision d = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertTrue(!d.shouldShowOverlay());
        assertEquals(Status.FAILED, repo.find(key).getStatus());
    }

    // ---------------------------------------------------------------
    // Resolver вернул REPLACE_WITH с тем же КМ → внутренняя ошибка.
    // ---------------------------------------------------------------
    @Test
    public void selfReplacement_rejectedAsInternalError() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.next(ResolveOutcome.replaceWith(KM_A));
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertTrue(!d.shouldShowOverlay());
        assertNull(repo.find(key));
    }

    // ---------------------------------------------------------------
    // Новый товар в том же чеке (другой GTIN) — отдельная запись.
    // ---------------------------------------------------------------
    @Test
    public void differentProducts_independentKeys() {
        CorrelationKey k1 = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        CorrelationKey k2 = CorrelationKey.of(SHOP, POS, RECEIPT, "04600439123456");

        resolver.next(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, k1, ctx(), RECEIPT_NUM);

        resolver.next(ResolveOutcome.replaceWith("01046004391234562199REPL"));
        sm.onScan("01046004391234562199ORIG", k2, ctx(), RECEIPT_NUM);

        assertNotNull(repo.find(k1));
        assertNotNull(repo.find(k2));
        assertEquals(2, repo.size());
    }

    // ---------------------------------------------------------------
    // UNAVAILABLE → REJECT без overlay, без создания state.
    // ---------------------------------------------------------------
    @Test
    public void unavailable_rejectsWithoutState() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.next(ResolveOutcome.unavailable("no candidate"));
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertTrue(!d.shouldShowOverlay());
        assertNull(repo.find(key));
    }

    // ---------------------------------------------------------------
    // При активном QR_SHOWN кассир сканирует посторонний валидный КМ C:
    // overlay должен закрыться (товар продан — замена не нужна).
    // ---------------------------------------------------------------
    @Test
    public void overlayActive_anyValidAcceptClosesOverlay() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.next(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);

        String km_c = "01" + GTIN + "21UNRELATED_VALID";
        resolver.next(ResolveOutcome.valid());
        Decision d = sm.onScan(km_c, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT_CLOSE_OVERLAY, d.getKind());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private ResolveContext ctx() {
        return new ResolveContext(GTIN, "4780069000130", "WATER_AND_BEVERAGES",
                SHOP, POS, RECEIPT, false);
    }

    private static final class FakeClock implements StateMachine.Clock {
        private long t;
        FakeClock(long start) { this.t = start; }
        @Override public long nowMs() { return t; }
        void advance(long delta) { t += delta; }
    }

    private static final class ScriptedResolver implements ReplacementResolver {
        private ResolveOutcome programmed;
        void next(ResolveOutcome o) { this.programmed = o; }
        @Override public ResolveOutcome resolve(String scannedKm, ResolveContext ctx) {
            ResolveOutcome o = programmed;
            if (o == null) {
                throw new AssertionError("ScriptedResolver: no outcome programmed for this call");
            }
            programmed = null;   // одноразово, чтобы не было "тихого" повторного использования
            return o;
        }
    }
}
