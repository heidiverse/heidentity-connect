import KotlinCoroutineSupport
import SwiftUI

/// Observes a Kotlin `StateFlow` and publishes its latest value to SwiftUI.
@MainActor
final class StateFlowObserver<Value>: ObservableObject {

    @Published private(set) var value: Value

    private var task: Task<Void, Never>?

    init(_ stateFlow: any KotlinTypedStateFlow<Value>) {
        value = stateFlow.value
        task = Task { @MainActor [weak self] in
            do {
                for try await value in stateFlow.asAsyncSequence() {
                    self?.value = value
                }
            } catch is CancellationError {
                // Expected when the observer is deallocated
            } catch {
                print("StateFlowObserver: Flow collection failed: \(error)")
            }
        }
    }

    deinit {
        task?.cancel()
    }
}
