#!/usr/bin/env python3
"""Quartz-based mouse drag. cliclick's dd/dm/du sometimes doesn't generate
Swing-AWT-recognized drag events for IntelliJ canvases; CGEvent does.

Usage: qdrag.py <from_x> <from_y> <to_x> <to_y> [steps] [step_delay_s]
"""
import sys, time
from Quartz import (
    CGEventCreateMouseEvent, CGEventPost,
    kCGEventLeftMouseDown, kCGEventLeftMouseUp, kCGEventLeftMouseDragged,
    kCGMouseButtonLeft, kCGHIDEventTap,
)

def post(evt_type, x, y):
    e = CGEventCreateMouseEvent(None, evt_type, (x, y), kCGMouseButtonLeft)
    CGEventPost(kCGHIDEventTap, e)

def main():
    fx, fy = float(sys.argv[1]), float(sys.argv[2])
    tx, ty = float(sys.argv[3]), float(sys.argv[4])
    steps = int(sys.argv[5]) if len(sys.argv) > 5 else 20
    delay = float(sys.argv[6]) if len(sys.argv) > 6 else 0.02

    post(kCGEventLeftMouseDown, fx, fy)
    time.sleep(0.1)
    for i in range(1, steps + 1):
        t = i / steps
        x = fx + (tx - fx) * t
        y = fy + (ty - fy) * t
        post(kCGEventLeftMouseDragged, x, y)
        time.sleep(delay)
    time.sleep(0.1)
    post(kCGEventLeftMouseUp, tx, ty)
    print(f"drag {fx},{fy} -> {tx},{ty} in {steps} steps")

if __name__ == "__main__":
    main()
