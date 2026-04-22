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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Тесты чистой логики StateMachine. В фокусе — multi-position семантика:
 * в одном {@link CorrelationKey} хранится несколько записей ({@code attemptIndex})
 * для нескольких одинаковых товаров в одном чеке.
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

        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        Decision d1 = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d1.getKind());
        assertEquals(KM_B, d1.getReplacementKm());
        assertTrue(d1.shouldShowOverlay());
        assertEquals(1, d1.getAttemptIndex());

        ReplacementState st = only(repo.findAll(key));
        assertEquals(Status.QR_SHOWN, st.getStatus());
        assertEquals(1, st.getAttemptIndex());
        assertEquals(KM_A, st.getOriginalKm());
        assertEquals(KM_B, st.getReplacementKm());

        resolver.enqueue(ResolveOutcome.valid());
        Decision d2 = sm.onScan(KM_B, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT_CLOSE_OVERLAY, d2.getKind());
        assertTrue(d2.shouldCloseOverlay());
        assertEquals(1, d2.getAttemptIndex());

        assertEquals(Status.REPLACEMENT_ACCEPTED, only(repo.findAll(key)).getStatus());
    }

    // ---------------------------------------------------------------
    // КМ сразу валиден → ACCEPT без overlay, без записи.
    // ---------------------------------------------------------------
    @Test
    public void validKm_acceptNoOverlay() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.enqueue(ResolveOutcome.valid());
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT, d.getKind());
        assertEquals(0, d.getAttemptIndex());
        assertTrue(repo.findAll(key).isEmpty());
    }

    // ---------------------------------------------------------------
    // Идемпотентный рескан того же плохого KM_A при висящем overlay:
    // новая запись НЕ создаётся, attemptIndex тот же.
    // ---------------------------------------------------------------
    @Test
    public void rescanSameBadKm_idempotent_keepsSameEntry() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        Decision d1 = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(1, d1.getAttemptIndex());

        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        Decision d2 = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d2.getKind());
        assertTrue(d2.shouldShowOverlay());
        assertEquals(1, d2.getAttemptIndex());
        assertEquals(KM_B, d2.getReplacementKm());

        assertEquals(1, repo.findAll(key).size());
    }

    // ---------------------------------------------------------------
    // Разные плохие KM для одного товара в одном чеке → разные attemptIndex.
    // Multi-position сценарий: 4 одинаковых кока-колы с 4 разными плохими КМ.
    // ---------------------------------------------------------------
    @Test
    public void multiPosition_fourBadKms_getFourIndependentEntries() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        for (int i = 1; i <= 4; i++) {
            String a = "01" + GTIN + "21BAD_" + i;
            String b = "01" + GTIN + "21REPL_" + i;
            resolver.enqueue(ResolveOutcome.replaceWith(b));
            Decision d = sm.onScan(a, key, ctx, RECEIPT_NUM);
            assertEquals("attemptIndex for scan #" + i, i, d.getAttemptIndex());
            assertEquals(b, d.getReplacementKm());
        }

        List<ReplacementState> all = repo.findAll(key);
        assertEquals(4, all.size());
        for (ReplacementState s : all) {
            assertEquals(Status.QR_SHOWN, s.getStatus());
        }
    }

    // ---------------------------------------------------------------
    // После 4 плохих КМ кассир сканирует третью замену — закрывается именно
    // третий overlay, остальные три остаются активными.
    // ---------------------------------------------------------------
    @Test
    public void multiPosition_scanReplacementClosesOnlyOwnOverlay() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        for (int i = 1; i <= 4; i++) {
            String a = "01" + GTIN + "21BAD_" + i;
            String b = "01" + GTIN + "21REPL_" + i;
            resolver.enqueue(ResolveOutcome.replaceWith(b));
            sm.onScan(a, key, ctx, RECEIPT_NUM);
        }

        String repl3 = "01" + GTIN + "21REPL_3";
        resolver.enqueue(ResolveOutcome.valid());
        Decision d = sm.onScan(repl3, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT_CLOSE_OVERLAY, d.getKind());
        assertEquals(3, d.getAttemptIndex());

        // Позиции 1,2,4 всё ещё QR_SHOWN; позиция 3 — REPLACEMENT_ACCEPTED.
        int qrShown = 0, accepted = 0;
        for (ReplacementState s : repo.findAll(key)) {
            if (s.getStatus() == Status.QR_SHOWN) qrShown++;
            else if (s.getStatus() == Status.REPLACEMENT_ACCEPTED) accepted++;
        }
        assertEquals(3, qrShown);
        assertEquals(1, accepted);
    }

    // ---------------------------------------------------------------
    // Валидный КМ, не являющийся ни одной из активных замен → обычный ACCEPT,
    // НИ ОДИН overlay не закрывается (в отличие от старой семантики).
    // Важно для multi-position: в чеке могут висеть другие оверлеи.
    // ---------------------------------------------------------------
    @Test
    public void unrelatedValid_doesNotCloseAnyOverlay() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);

        String km_c = "01" + GTIN + "21UNRELATED_VALID";
        resolver.enqueue(ResolveOutcome.valid());
        Decision d = sm.onScan(km_c, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT, d.getKind());
        assertFalse(d.shouldCloseOverlay());
        assertEquals(Status.QR_SHOWN, only(repo.findAll(key)).getStatus());
    }

    // ---------------------------------------------------------------
    // TTL: overlay истёк, повторный скан того же A → новая запись с index=2.
    // ---------------------------------------------------------------
    @Test
    public void expiredState_allowsNewReplacementCycle() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(Status.QR_SHOWN, only(repo.findAll(key)).getStatus());

        clock.advance(61_000L);

        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        Decision d = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertTrue(d.shouldShowOverlay());
        assertEquals(2, d.getAttemptIndex());

        // В репо теперь две записи: старая EXPIRED и свежая QR_SHOWN.
        List<ReplacementState> all = repo.findAll(key);
        assertEquals(2, all.size());
        int expired = 0, qrShown = 0;
        for (ReplacementState s : all) {
            if (s.getStatus() == Status.EXPIRED) expired++;
            else if (s.getStatus() == Status.QR_SHOWN) qrShown++;
        }
        assertEquals(1, expired);
        assertEquals(1, qrShown);
    }

    // ---------------------------------------------------------------
    // Resolver вернул REPLACE_WITH с тем же КМ → внутренняя ошибка, без записи.
    // ---------------------------------------------------------------
    @Test
    public void selfReplacement_rejectedAsInternalError() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.enqueue(ResolveOutcome.replaceWith(KM_A));
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertFalse(d.shouldShowOverlay());
        assertTrue(repo.findAll(key).isEmpty());
    }

    // ---------------------------------------------------------------
    // Разные товары в том же чеке — отдельные базовые ключи.
    // ---------------------------------------------------------------
    @Test
    public void differentProducts_independentKeys() {
        CorrelationKey k1 = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        CorrelationKey k2 = CorrelationKey.of(SHOP, POS, RECEIPT, "04600439123456");

        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, k1, ctx(), RECEIPT_NUM);

        resolver.enqueue(ResolveOutcome.replaceWith("01046004391234562199REPL"));
        sm.onScan("01046004391234562199ORIG", k2, ctx(), RECEIPT_NUM);

        assertEquals(1, repo.findAll(k1).size());
        assertEquals(1, repo.findAll(k2).size());
        assertEquals(2, repo.size());
    }

    // ---------------------------------------------------------------
    // UNAVAILABLE → REJECT без overlay, без создания state.
    // ---------------------------------------------------------------
    @Test
    public void unavailable_rejectsWithoutState() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.enqueue(ResolveOutcome.unavailable("no candidate"));
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertFalse(d.shouldShowOverlay());
        assertTrue(repo.findAll(key).isEmpty());
    }

    // ---------------------------------------------------------------
    // Сценарий: позиция с подменой удалена, SR10 снова просит КМ,
    // кассир сканирует оригинальный плохой КМ → реоткрываем тот же QR.
    // Резолвер НЕ вызывается (иначе ScriptedResolver бы упал).
    // ---------------------------------------------------------------
    @Test
    public void reopenOverlay_afterPositionDelete_onOriginalRescan() {
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        ResolveContext ctx = ctx();

        // 1) Обычный цикл замены: A плохой → QR (KM_B), B → ACCEPT_CLOSE_OVERLAY.
        resolver.enqueue(ResolveOutcome.replaceWith(KM_B, "RID-1"));
        Decision d1 = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(1, d1.getAttemptIndex());

        resolver.enqueue(ResolveOutcome.valid());
        Decision d2 = sm.onScan(KM_B, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT_CLOSE_OVERLAY, d2.getKind());
        ReplacementState after = only(repo.findAll(key));
        assertEquals(Status.REPLACEMENT_ACCEPTED, after.getStatus());
        assertEquals("RID-1", after.getReservationId());

        // 2) Кассир удаляет позицию и SR10 снова просит КМ. Кассир сканирует
        //    тот же плохой KM_A. Резолвер НЕ должен быть вызван — очередь пуста.
        Decision d3 = sm.onScan(KM_A, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d3.getKind());
        assertTrue(d3.shouldShowOverlay());
        assertEquals("reopened with the same attemptIndex", 1, d3.getAttemptIndex());
        assertEquals("reopened with the same replacementKm", KM_B, d3.getReplacementKm());

        ReplacementState reopened = only(repo.findAll(key));
        assertEquals(Status.QR_SHOWN, reopened.getStatus());
        assertEquals("reservation preserved", "RID-1", reopened.getReservationId());

        // 3) Кассир снова сканирует KM_B → закрываем overlay, возвращаемся в ACCEPTED.
        resolver.enqueue(ResolveOutcome.valid());
        Decision d4 = sm.onScan(KM_B, key, ctx, RECEIPT_NUM);
        assertEquals(DecisionKind.ACCEPT_CLOSE_OVERLAY, d4.getKind());
        assertEquals(Status.REPLACEMENT_ACCEPTED, only(repo.findAll(key)).getStatus());
    }

    // ---------------------------------------------------------------
    // replacement.enabled=false → реоткрытия нет, даже если ACCEPTED запись есть.
    // ---------------------------------------------------------------
    @Test
    public void reopenOverlay_disabledInValidatorMode() {
        // Готовим ACCEPTED-запись вручную (обычный режим).
        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        resolver.enqueue(ResolveOutcome.valid());
        sm.onScan(KM_B, key, ctx(), RECEIPT_NUM);
        assertEquals(Status.REPLACEMENT_ACCEPTED, only(repo.findAll(key)).getStatus());

        // Переключаемся в режим валидатора и сканируем тот же KM_A.
        config = new KmReplacementConfig("http://x", 3000, 5000, 60_000, 2, false, false);
        sm = new StateMachine(resolver, repo, config, clock);

        // В режиме валидатора резолвер вызывается как обычно; вернёт REPLACE_WITH.
        // Ожидаем plain REJECT без overlay, ACCEPTED-запись не трогается.
        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);
        assertEquals(DecisionKind.REJECT, d.getKind());
        assertFalse(d.shouldShowOverlay());
        assertEquals(Status.REPLACEMENT_ACCEPTED, only(repo.findAll(key)).getStatus());
    }

    // ---------------------------------------------------------------
    // replacement.enabled=false → плагин как обычный валидатор:
    // REPLACE_WITH от резолвера понижается до REJECT без overlay и без state.
    // ---------------------------------------------------------------
    @Test
    public void replacementDisabled_replaceWithDowngradedToPlainReject() {
        // Пересобираем SM с disabled-режимом.
        config = new KmReplacementConfig("http://x", 3000, 5000, 60_000, 2, false, false);
        sm = new StateMachine(resolver, repo, config, clock);

        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.enqueue(ResolveOutcome.replaceWith(KM_B));
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);

        assertEquals(DecisionKind.REJECT, d.getKind());
        assertFalse("overlay must not be shown in validator mode", d.shouldShowOverlay());
        assertNull(d.getReplacementKm());
        assertTrue("no state must be persisted in validator mode", repo.findAll(key).isEmpty());
    }

    // ---------------------------------------------------------------
    // replacement.enabled=false, но KM валиден → обычный ACCEPT.
    // ---------------------------------------------------------------
    @Test
    public void replacementDisabled_validKmStillAccepted() {
        config = new KmReplacementConfig("http://x", 3000, 5000, 60_000, 2, false, false);
        sm = new StateMachine(resolver, repo, config, clock);

        CorrelationKey key = CorrelationKey.of(SHOP, POS, RECEIPT, GTIN);
        resolver.enqueue(ResolveOutcome.valid());
        Decision d = sm.onScan(KM_A, key, ctx(), RECEIPT_NUM);

        assertEquals(DecisionKind.ACCEPT, d.getKind());
        assertTrue(repo.findAll(key).isEmpty());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private ResolveContext ctx() {
        return new ResolveContext(GTIN, "4780069000130", "WATER_AND_BEVERAGES",
                SHOP, POS, RECEIPT, false);
    }

    private static ReplacementState only(List<ReplacementState> list) {
        assertNotNull(list);
        assertEquals("expected exactly 1 state", 1, list.size());
        return list.get(0);
    }

    private static final class FakeClock implements StateMachine.Clock {
        private long t;
        FakeClock(long start) { this.t = start; }
        @Override public long nowMs() { return t; }
        void advance(long delta) { t += delta; }
    }

    private static final class ScriptedResolver implements ReplacementResolver {
        private final Deque<ResolveOutcome> queue = new ArrayDeque<ResolveOutcome>();
        void enqueue(ResolveOutcome o) { queue.addLast(o); }
        @Override public ResolveOutcome resolve(String scannedKm, ResolveContext ctx) {
            ResolveOutcome o = queue.pollFirst();
            if (o == null) {
                throw new AssertionError("ScriptedResolver: no outcome programmed for this call");
            }
            return o;
        }
    }
}
