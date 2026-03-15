# Networking Plan

## Foundation rule

Multiplayer is not bolted on later. Session 1 does not ship live networking, but it does enforce the correct ownership boundaries now.

## Authority

- The host owns world state, chunk generation, and future interaction validation.
- The client owns rendering, local input capture, and presentation.
- Even local single-player runs through an embedded host session.

## Early sync targets

The first real networkable milestone should prove:

- two players in the same block world
- synchronized chunk and block state
- synchronized movement
- synchronized basic interactions

## Data boundaries to preserve

- authoritative world state
- chunk data
- entity simulation
- inventory state
- crafting actions
- interaction requests

## Session 1 implementation hooks

- `GameSessionMode` defines long-term runtime modes.
- `LocalHostSession` is the embedded host path.
- `InteractionRequest` reserves a DTO shape for future network-safe interactions, including block upgrades.
