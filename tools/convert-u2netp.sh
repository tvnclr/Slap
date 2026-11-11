#!/usr/bin/env bash
# Rebuilds app/src/main/assets/models/u2netp.tflite from the original U^2-Net (u2netp) weights.
# Model: U^2-Net by Xuebin Qin et al., Apache-2.0 (https://github.com/xuebinqin/U-2-Net).
# Tools: pip install onnx==1.20.1 onnxsim==0.6.5 onnx2tf==2.6.9 tensorflow-cpu==2.21.0
set -euo pipefail
work=$(mktemp -d); cd "$work"
curl -sSfL -o u2netp.onnx https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx
echo "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8  u2netp.onnx" | sha256sum -c -
# keep only the fused output (d0) the app uses
python3 - <<'PY'
import onnx
m = onnx.load("u2netp.onnx")
keep = [o for o in m.graph.output if o.name == "1959"]
del m.graph.output[:]; m.graph.output.extend(keep)
onnx.save(m, "u2netp_d0.onnx")
PY
onnxsim u2netp_d0.onnx u2netp_sim.onnx --overwrite-input-shape input.1:1,3,320,320
onnx2tf -i u2netp_sim.onnx -o out -n >/dev/null
sha256sum out/u2netp_sim_float32.tflite
echo "copy out/u2netp_sim_float32.tflite to app/src/main/assets/models/u2netp.tflite"
echo "$work"
