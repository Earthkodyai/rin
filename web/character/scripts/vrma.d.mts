// Types for vrma.mjs, so the page's tests (src/gesture.test.ts) can typecheck against it.
import type * as THREE from 'three';

export type ArmChannel = number[];
export interface GestureKey {
  t: number;
  [channel: string]: number | number[] | string; // a number array, or "rest"
}
export interface GestureDef {
  duration?: number;
  keys: GestureKey[];
  face?: Record<string, number[][]>; // [t, weight] pairs
}
export const FPS: number;
export const SKELETON: Record<string, [string | null, number[]]>;
export const ARM_REST: { left: ArmChannel; right: ArmChannel };
export function solveArm(
  side: 'left' | 'right',
  channel: ArmChannel,
): { upper: THREE.Quaternion; lower: THREE.Quaternion; reach: number; elbow: THREE.Vector3; hand: THREE.Vector3 };
export function clearArm(
  side: 'left' | 'right',
  channel: ArmChannel,
  grip?: { turn: number[]; curl: number; shoulder?: number[] },
): ArmChannel;
export function restArm(side: 'left' | 'right'): { upper: THREE.Quaternion; lower: THREE.Quaternion };
export function restPosition(bone: string): THREE.Vector3;
export function restFingers(side: 'left' | 'right'): Record<string, THREE.Quaternion>;
export function pushOut(point: THREE.Vector3, radius: number): [number, number];
export function poseAt(gesture: GestureDef, t: number): Record<string, THREE.Quaternion>;
export function validate(name: string, gesture: GestureDef): string[];
export function buildVrma(name: string, gesture: GestureDef): Uint8Array;
