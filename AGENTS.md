# Agent Instructions

This project is a Kotlin Android project using MVVM and Jetpack Compose.
Project-specific product requirements will be added later, so keep new work
focused on the architecture and conventions below unless the user gives more
context.

## Project Layout

- `screens/view/`: Jetpack Compose UI. Put composable screen/view code here.
- `screens/viewmodel/`: ViewModels and UI state/event handling for screens.
- `model/`: Model layer code, including data models and domain objects.

Keep the top-level architecture organized around these directories until the
project gains a fuller Android/Gradle module structure. If an Android app module
is added later, preserve the same separation inside the Kotlin source set unless
the user changes the layout.

## Architecture

- Follow MVVM boundaries.
- Views should render state and send user actions upward.
- ViewModels should own UI state, screen events, and coordination logic.
- Model classes should not depend on Compose UI APIs.
- Prefer immutable UI state data classes and explicit event/action types.
- Keep business/domain state out of composables when it belongs in a ViewModel
  or model object.

## Jetpack Compose

- Build UI with composable functions.
- Keep composables small enough to preview, test, and reuse.
- Hoist state where practical; avoid storing long-lived screen state directly in
  composables.
- Use `Modifier` parameters on reusable composables.
- Keep previews near the composables they exercise when previews are added.

## Kotlin Style

- Prefer clear names over abbreviations.
- Use `data class` for simple immutable state and model containers.
- Keep files scoped to one clear responsibility.
- Add comments only where they explain non-obvious behavior or decisions.

## Verification

- Run the most specific available checks after changes.
- If no build or test command exists yet, state that clearly when reporting work.
- Do not invent project requirements before the user provides more context.
