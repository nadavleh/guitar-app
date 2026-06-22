// Minimal RNG abstraction mirroring the slice of kotlin.random.Random the ear-
// training code uses. Defaults to Math.random; injectable for deterministic tests.

export interface Rng {
  int(bound: number): number; // 0 <= result < bound
  bool(): boolean;
}

export const defaultRng: Rng = {
  int: (bound: number) => Math.floor(Math.random() * bound),
  bool: () => Math.random() < 0.5,
};
