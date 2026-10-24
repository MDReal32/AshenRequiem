// ProbeJS types every java.lang.Object parameter as its `$Object` class, so plain JS values don't fit one, although
// KubeJS passes any value there (`console.info("text")`, `set.contains("tag")`). Giving JS primitives Object's
// members lets them through.

interface JavaObjectMembers {
  equals(other: unknown): boolean;
  hashCode(): number;
  getClass(): never;
  notify(): void;
  notifyAll(): void;
  wait(millis?: number, nanos?: number): void;
  readonly class: never;
}

interface String extends JavaObjectMembers {}
interface Number extends JavaObjectMembers {}
interface Boolean extends JavaObjectMembers {}
