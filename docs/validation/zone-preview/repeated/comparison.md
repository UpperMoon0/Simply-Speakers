# Repeated actual Minecraft measurements

All values are milliseconds; each cell is median / p95. Every run is included.
Preview GPU timestamps measure a submission span, including pipeline idle time; they are not isolated GPU busy time. Particle scope is the complete particle engine in the otherwise empty fixture; shader scope is the zone draw method.

| Run | Scene | Whole CPU | Whole GPU | Frame interval | Preview CPU | Preview GPU span |
|---|---|---:|---:|---:|---:|---:|
| 01-shader | fallback | 4.625 / 11.239 | 2.626 / 5.735 | 8.161 / 15.787 | 0.001 / 0.002 | 0.001 / 0.002 |
| 01-shader | large-inside | 4.789 / 11.275 | 2.465 / 6.079 | 8.153 / 16.013 | 0.028 / 0.066 | 0.023 / 0.026 |
| 01-shader | large-outside | 4.437 / 10.184 | 2.088 / 5.575 | 6.940 / 12.862 | 0.028 / 0.065 | 0.007 / 0.008 |
| 01-shader | portable | 4.958 / 9.074 | 2.591 / 5.600 | 8.501 / 13.589 | 0.034 / 0.075 | 0.033 / 0.036 |
| 01-shader | small-inside | 4.929 / 8.848 | 2.424 / 5.905 | 8.212 / 13.009 | 0.029 / 0.065 | 0.030 / 0.032 |
| 01-shader | small-outside | 5.858 / 9.256 | 2.693 / 5.996 | 9.808 / 16.526 | 0.033 / 0.078 | 0.033 / 0.038 |
| 01-shader | terrain | 4.776 / 10.471 | 2.657 / 5.595 | 8.108 / 13.818 | 0.026 / 0.060 | 0.032 / 0.035 |
| 02-particles | large-inside | 5.782 / 10.739 | 2.144 / 5.092 | 8.488 / 15.357 | 1.124 / 1.920 | 0.035 / 0.039 |
| 02-particles | large-outside | 5.271 / 6.252 | 1.876 / 4.170 | 6.407 / 9.764 | 1.127 / 1.392 | 0.010 / 0.013 |
| 02-particles | portable | 5.872 / 12.576 | 2.563 / 6.316 | 9.816 / 19.484 | 1.125 / 2.820 | 0.061 / 0.072 |
| 02-particles | small-inside | 5.496 / 6.611 | 1.774 / 4.191 | 6.628 / 10.683 | 1.044 / 1.309 | 0.026 / 0.028 |
| 02-particles | small-outside | 5.905 / 8.378 | 1.947 / 4.591 | 7.299 / 13.658 | 1.093 / 1.639 | 0.018 / 0.019 |
| 02-particles | terrain | 5.533 / 12.083 | 2.390 / 5.623 | 9.385 / 18.992 | 1.085 / 2.681 | 0.052 / 0.058 |
| 03-particles | large-inside | 5.930 / 13.265 | 2.334 / 5.777 | 9.523 / 21.562 | 1.197 / 3.094 | 0.038 / 0.040 |
| 03-particles | large-outside | 5.288 / 8.510 | 1.838 / 4.447 | 7.348 / 12.512 | 1.185 / 1.993 | 0.019 / 0.025 |
| 03-particles | portable | 5.803 / 18.602 | 2.663 / 6.648 | 10.086 / 26.860 | 1.144 / 3.743 | 0.060 / 0.074 |
| 03-particles | small-inside | 5.642 / 12.183 | 2.072 / 4.770 | 8.172 / 15.389 | 1.125 / 2.281 | 0.060 / 0.066 |
| 03-particles | small-outside | 5.739 / 12.339 | 2.187 / 4.996 | 8.335 / 17.398 | 1.122 / 2.534 | 0.044 / 0.048 |
| 03-particles | terrain | 5.465 / 12.169 | 2.420 / 5.239 | 9.306 / 16.989 | 1.097 / 2.813 | 0.052 / 0.056 |
| 04-shader | fallback | 4.532 / 11.283 | 2.622 / 6.927 | 8.246 / 16.357 | 0.002 / 0.004 | 0.001 / 0.002 |
| 04-shader | large-inside | 4.421 / 5.150 | 1.859 / 4.162 | 5.494 / 7.498 | 0.026 / 0.049 | 0.005 / 0.005 |
| 04-shader | large-outside | 4.091 / 4.618 | 1.799 / 4.030 | 5.129 / 6.835 | 0.026 / 0.034 | 0.003 / 0.004 |
| 04-shader | portable | 4.588 / 10.981 | 2.606 / 6.409 | 8.352 / 14.668 | 0.027 / 0.065 | 0.033 / 0.038 |
| 04-shader | small-inside | 4.457 / 5.175 | 1.787 / 4.098 | 5.597 / 7.389 | 0.026 / 0.049 | 0.007 / 0.008 |
| 04-shader | small-outside | 4.875 / 6.059 | 2.033 / 4.332 | 6.031 / 8.371 | 0.027 / 0.051 | 0.007 / 0.008 |
| 04-shader | terrain | 4.486 / 10.424 | 2.425 / 5.489 | 8.023 / 15.125 | 0.025 / 0.058 | 0.031 / 0.034 |
