package com.llexcel;

import java.util.*;

public class SurveyMath {
    private static final double[] GaussRr = {0.1739274226, 0.3260725774, 0.3260725774, 0.1739274226};
    private static final double[] GaussVv = {0.0694318442, 0.3300094782, 0.6699905218, 0.9305681558};

    public static String num2K(double meters) {
        int km = (int) Math.floor(meters / 1000.0);
        double m = Math.round((meters - km * 1000.0) * 100.0) / 100.0;
        return (m == (int) m) ? String.format(Locale.US, "K%d+%03d", km, (int) m) : String.format(Locale.US, "K%d+%06.2f", km, m);
    }

    public static double dmsToRad(double dms) {
        int sign = dms >= 0 ? 1 : -1;
        dms = Math.abs(dms);
        int deg = (int) dms;
        double ms = (dms - deg) * 100.0;
        int min = (int) Math.floor(ms + 1e-9);
        double sec = (ms - min) * 100.0;
        if (sec >= 59.9999) { sec = 0; min++; }
        if (min >= 60) { min = 0; deg++; }
        return sign * (deg + min / 60.0 + sec / 3600.0) * Math.PI / 180.0;
    }

    public static double radToDms(double rad) {
        double deg = rad * (180.0 / Math.PI);
        int sign = deg >= 0 ? 1 : -1;
        deg = Math.abs(deg);
        int d = (int) deg;
        double rem = (deg - d) * 60.0;
        int m = (int) rem;
        double s = Math.round((rem - m) * 60.0 * 10.0) / 10.0;
        if (s >= 60.0) { s = 0; m++; }
        if (m >= 60) { m = 0; d++; }
        return sign * (d + m / 100.0 + s / 10000.0);
    }

    public static double[] fwj(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double hd = Math.atan2(dy, dx);
        if (hd < 0) hd += 2 * Math.PI;
        return new double[]{Math.hypot(dx, dy), hd};
    }

    public static double[] zs(double xyk, double xyx, double xyy, double xyhd, double xycd,
                              double xyqdr, double xyzdr, double xyzy, double jsk, double jsb, double jdDms) {
        double jd = dmsToRad(jdDms);
        double w = jsk - xyk;
        if (Math.abs(xyqdr - xyzdr) < 0.01 && xyqdr > 0) {
            double a = xyhd + xyzy * Math.PI / 2.0;
            double da = w / xyqdr * xyzy;
            double ta = (xyhd + da + 2 * Math.PI) % (2 * Math.PI);
            double x = xyx + xyqdr * Math.cos(a) - xyqdr * Math.cos(a + da) + jsb * Math.cos(a + da - xyzy * Math.PI / 2.0 + jd);
            double y = xyy + xyqdr * Math.sin(a) - xyqdr * Math.sin(a + da) + jsb * Math.sin(a + da - xyzy * Math.PI / 2.0 + jd);
            return new double[]{x, y, ta};
        }
        if (xyqdr < 0.01 && xyzdr < 0.01 && Math.abs(xyzy) < 0.01) {
            return new double[]{xyx + w * Math.cos(xyhd) + jsb * Math.cos(xyhd + jd),
                                xyy + w * Math.sin(xyhd) + jsb * Math.sin(xyhd + jd), xyhd};
        }
        double qr = xyqdr < 0.001 ? 99999999 : xyqdr;
        double zr = xyzdr < 0.001 ? 99999999 : xyzdr;
        double c = 1.0 / qr, d = (qr - zr) / (2.0 * xycd * qr * zr);
        double xs = 0, ys = 0;
        for (int i = 0; i < 4; i++) {
            double v = GaussVv[i], r = GaussRr[i];
            double f = xyhd + xyzy * v * w * (c + v * w * d);
            xs += r * Math.cos(f); ys += r * Math.sin(f);
        }
        double fhz3 = ((xyhd + xyzy * w * (c + w * d)) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        return new double[]{xyx + w * xs + jsb * Math.cos(fhz3 + jd), xyy + w * ys + jsb * Math.sin(fhz3 + jd), fhz3};
    }

    public static double[] pqxFwd(double[][] pqx, double k, double b, double jdDms) {
        if (pqx == null || pqx.length == 0) return new double[]{0, 0, 0};
        for (double[] seg : pqx) {
            if (k >= seg[0] - 1e-6 && k <= seg[0] + seg[4] + 1e-6) {
                return zs(seg[0], seg[1], seg[2], dmsToRad(seg[3]), seg[4], seg[5], seg[6], seg[7], k, b, jdDms);
            }
        }
        return new double[]{0, 0, 0};
    }

    public static double[] pqxInv(double[][] pqx, double fsx, double fsy) {
        if (pqx == null || pqx.length == 0) return new double[]{0, 0};
        double k = pqx[0][0], qdlc = pqx[0][0], zdlc = pqx[pqx.length - 1][0] + pqx[pqx.length - 1][4];
        double[] p0 = pqxFwd(pqx, k, 0, 0);
        double[] jljd = fwj(p0[0], p0[1], fsx, fsy);
        double cz = jljd[0] * Math.cos(jljd[1] - p0[2]);
        double pj = jljd[0] * Math.sin(jljd[1] - p0[2]);
        int iter = 0;
        while (Math.abs(cz) > 0.005 && iter++ < 20) {
            k += cz;
            if (k < qdlc) return new double[]{-1, -1};
            if (k > zdlc) return new double[]{-2, -2};
            p0 = pqxFwd(pqx, k, 0, 0);
            jljd = fwj(p0[0], p0[1], fsx, fsy);
            cz = jljd[0] * Math.cos(jljd[1] - p0[2]);
            pj = jljd[0] * Math.sin(jljd[1] - p0[2]);
        }
        return new double[]{k, pj};
    }

    public static double sqxH(double[][] sqx, double k) {
        int n = sqx.length;
        if (n == 0 || k < sqx[0][0] || k > sqx[n - 1][0]) return -1;
        if (n == 1) return sqx[0][1];
        int idx = -1;
        for (int i = 0; i < n - 1; i++) if (k >= sqx[i][0] && k <= sqx[i + 1][0]) { idx = i; break; }
        if (idx == -1) return -1;
        double z1 = sqx[idx][0], h1 = sqx[idx][1], z2 = sqx[idx + 1][0], h2 = sqx[idx + 1][1];
        double res = h1 + (h2 - h1) / (z2 - z1) * (k - z1);
        for (int p : new int[]{idx, idx + 1}) {
            if (p <= 0 || p >= n - 1) continue;
            double r = sqx[p][2];
            if (r <= 0) continue;
            double zv = sqx[p][0], hv = sqx[p][1];
            double i1 = (hv - sqx[p - 1][1]) / (zv - sqx[p - 1][0]);
            double i2 = (sqx[p + 1][1] - hv) / (sqx[p + 1][0] - zv);
            double omega = i2 - i1, T = r * Math.abs(omega) / 2.0;
            if (k >= zv - T && k <= zv + T) {
                double tanElev = (k <= zv) ? hv + i1 * (k - zv) : hv + i2 * (k - zv);
                double x = (k <= zv) ? (k - (zv - T)) : ((zv + T) - k);
                res = tanElev + x * x / (2.0 * r) * Math.signum(omega);
            }
        }
        return res;
    }

    public static double[] gaussProj(double L, double B, double lonCenter) {
        double pi = Math.PI, p0 = 206264.80624709636, e = 0.00669438002290, e1 = 0.00673949677548, b = 6356752.3141, a = 6378137.0;
        double Lc = lonCenter >= 359 ? 3.0 * Math.floor(L / 3.0 + 0.5) : lonCenter;
        double l = (L - Lc) * 3600.0;
        B = B * pi / 180.0;
        double M0 = a * (1 - e), M2 = 1.5 * e * M0, M4 = 1.25 * e * M2, M6 = (7.0 / 6.0) * e * M4, M8 = 1.125 * e * M6;
        double a0 = M0 + M2 / 2 + 3 * M4 / 8 + 5 * M6 / 16 + 35 * M8 / 128;
        double a2 = M2 / 2 + M4 / 2 + 15 * M6 / 32 + 7 * M8 / 16;
        double a4 = M4 / 8 + 3 * M6 / 16 + 7 * M8 / 32;
        double a6 = M6 / 32 + M8 / 16;
        double Xz = a0 * B - a2 / 2 * Math.sin(2 * B) + a4 / 4 * Math.sin(4 * B) - a6 / 6 * Math.sin(6 * B);
        double V = Math.sqrt(1 + e1 * Math.cos(B) * Math.cos(B)), N = (a * a / b) / V, t = Math.tan(B), n = e1 * Math.cos(B) * Math.cos(B);
        double x = Xz + N / 2 * Math.sin(B) * Math.cos(B) * l * l / (p0 * p0) + N / 24 * Math.sin(B) * Math.pow(Math.cos(B), 3) * (5 - t * t + 9 * n) * Math.pow(l / p0, 4);
        double y = 500000.0 + N * Math.cos(B) * (l / p0) + N / 6 * Math.pow(Math.cos(B), 3) * (1 - t * t + n) * Math.pow(l / p0, 3);
        return new double[]{x, y};
    }

    public static double[] utmProj(double lon, double lat) {
        double a = 6378137.0, f = 1.0 / 298.257223563, e2 = 2 * f - f * f, ep2 = e2 / (1 - e2), k0 = 0.9996;
        int zone = (int) Math.floor((lon + 180.0) / 6.0) + 1;
        double lon0 = (zone - 1) * 6.0 - 180.0 + 3.0;
        double phi = Math.toRadians(lat), lam = Math.toRadians(lon), lam0 = Math.toRadians(lon0);
        double N = a / Math.sqrt(1 - e2 * Math.sin(phi) * Math.sin(phi)), T = Math.tan(phi) * Math.tan(phi), C = ep2 * Math.cos(phi) * Math.cos(phi), A = (lam - lam0) * Math.cos(phi);
        double M = a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * phi - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * Math.sin(2 * phi) + (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * Math.sin(4 * phi) - (35 * e2 * e2 * e2 / 3072) * Math.sin(6 * phi));
        double east = 500000.0 + k0 * N * (A + (1 - T + C) * A * A * A / 6 + (5 - 18 * T + T * T + 72 * C - 58 * ep2) * Math.pow(A, 5) / 120);
        double north = k0 * (M + N * Math.tan(phi) * (A * A / 2 + (5 - T + 9 * C + 4 * C * C) * Math.pow(A, 4) / 24 + (61 - 58 * T + T * T + 600 * C - 330 * ep2) * Math.pow(A, 6) / 720));
        if (lat < 0) north += 10000000.0;
        return new double[]{north, east};
    }
}
