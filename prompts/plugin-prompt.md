# Prompt: sbg-set10-marking-plugin

You are implementing `sbg-set10-marking-plugin` for Set Retail 10 (`ExciseValidationPluginExtended`).

## Goal

When cashier scans product barcode for marked goods:

- send scanned KM + product context to backend;
- if scanned KM exists and suitable, accept it;
- if scanned KM not found, backend auto-selects first suitable FIFO KM;
- plugin returns success to POS with selected/validated KM flow.

No manual KM chooser UI is required.

## Integration rules

- Use `validateExciseForSale(...)` and `validateExciseForRefund(...)` callbacks.
- Send `resolve-and-reserve` / `return-resolve-and-reserve`.
- On receipt fiscalization:
  - sale success -> `sold-confirm`
  - sale cancel -> `sale-release`
  - refund success -> `return-confirm`
  - refund cancel -> `return-release`
- Store retry payload in `Feedback` and resend in `onRepeatSend`.
- After successful repeat-send, clear local reservation mapping.

## Configuration

- Read service URL and timeouts from `IntegrationProperties`.
- Respect connect/read timeout per request.
- Default backend URL: `http://localhost:8080`.

## Compatibility constraints

- Keep Set10 API compatibility (`User#getTabNumber`, no `getLogin`).
- Plugin ID and naming must contain `sbg`.
- Preserve existing `metainf.xml` external service and options.
