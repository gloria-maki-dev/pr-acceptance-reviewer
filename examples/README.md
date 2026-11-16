# Ejemplos de la demo

Ticket ficticio **TIENDA-412 — Aclarar el importe del carrito**, de una tienda online.

Estos tres archivos son la única fuente de los ejemplos: el frontend los importa con
`?raw` y las pruebas del backend los leen desde disco, así que la demo de la pantalla
y la de las pruebas no pueden desincronizarse.

| Archivo | Qué contiene |
|---|---|
| `criterios.txt` | Los dos criterios de aceptación del ticket, uno por línea. |
| `diff-con-fallo.diff` | Cambia «Total» en `CartSummary.tsx` **y también** en `OrderHistory.tsx`. Resultado esperado: criterio 1 **Cumplido**, criterio 2 **No cumplido** citando `OrderHistory.tsx`. |
| `diff-corregido.diff` | Cambia «Total» solo en `CartSummary.tsx`. Resultado esperado: ambos criterios **Cumplidos**. |
