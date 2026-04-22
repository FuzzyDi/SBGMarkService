package uz.sbg.kmreplacement.resolver;

/**
 * Источник истины о валидности КМ и наличии замен.
 *
 * <p>Любая реализация обязана:
 * <ul>
 *   <li>никогда не возвращать {@code null};</li>
 *   <li>никогда не бросать исключения за пределы — упаковывать сетевые/БД-ошибки
 *       в {@link ResolveOutcome#error(String)};</li>
 *   <li>для {@code REPLACE_WITH} возвращать КМ, <b>отличный от входного</b>.</li>
 * </ul></p>
 */
public interface ReplacementResolver {

    /**
     * @param scannedKm отсканированный кассиром КМ
     * @param ctx        контекст (gtin, barcode, productType, shop/pos/receipt)
     */
    ResolveOutcome resolve(String scannedKm, ResolveContext ctx);
}
