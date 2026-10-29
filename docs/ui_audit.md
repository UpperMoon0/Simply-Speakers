# UI verification

The player-facing speaker, proxy, playlist, settings, and controller
screens should be verified on every supported native UI adapter. Use
the loader's opt-in UI fixture and test small and normal viewports,
both themes, long and localized labels, scroll clipping, focus,
tooltips, dialogs, playlist actions and transport controls. Check
that hit targets and text bounds match what is actually rendered.

Automated geometry and interaction assertions do not replace visual
review. Preserve screenshots and exact test receipts for each
release. Do not infer a pass for one loader from a different loader's
successful run, or count an interrupted run as complete.

Historical screenshots, run counts and scoped pass/failure evidence
are retained in the [dated UI audit](../changelog/2026-10-ui-audit.md).
