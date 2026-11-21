# OpenVPN engine third-party notice

NEXUS Browser's VPN feature builds against the vpnLib module from
[IyedAmiri/OpenGateVPN](https://github.com/IyedAmiri/OpenGateVPN), pinned to
commit 12f8ea97976f1bac0307a013b40712098b865d43.

The engine module is distributed under GNU GPL version 3 in that repository.
It is based on OpenVPN for Android and contains copyright and license notices
from its upstream contributors. The exact upstream module source, including
native libraries and its license file, is fetched at build time by
.github/workflows/nexus-orbit-baseline.yml. NEXUS-specific integration
changes are made by .nexus-overlay/scripts/apply-openvpn-engine.py.

Upstream source:
https://github.com/IyedAmiri/OpenGateVPN/tree/12f8ea97976f1bac0307a013b40712098b865d43/vpnLib

License:
https://github.com/IyedAmiri/OpenGateVPN/blob/12f8ea97976f1bac0307a013b40712098b865d43/vpnLib/LICENSE

The upstream GPL terms apply to the engine module and distribution of combined
derivative works. Review the complete licenses and corresponding-source
obligations before distributing NEXUS binaries.
