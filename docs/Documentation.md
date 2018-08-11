Meshenger documentation
=============
## Motivation
Currently, the market of VoIP software is dominated by software owned by big companies and tied to central infrastructures in order to monetize the platform and control the data flow.
Individuals and communities like Freifunk often try to establish decentralized 
standards, which should provide a less-controlled, faster, and more independent internet without having any financial interests.
Yet, the hotspots provided by e.g. Freifunk are often simply perceived as internet access points,
thus we wanted to provide a proof-of-concept demonstrating the use of any network without having an internet connection.
Of course, the use of such an app would not be restricted to Freifunk networks, it should also be applicable in disaster areas, where internet access is cut off, home networks, fairs etc.

As practical as this approach may seem, as of now there are only a small number of practical applications
which provide a serious alternative to established apps like WhatsApp, Facebook etc.
Thus, our goal was to create an open-source, decentralized messenger supporting audio- as well as video calling through WebRTC,
that makes it as easy to add a contact as scanning a qr-code on another phone.

We decided to start coding for android, since there is a huge community, generally a lot of documentation, and Android is simply the most wide-spread platform.
One of our goals was to be able to operate in networks without a DHCP server, more on that in the IPv6 part.
Since many community networks block Broadcast/Multicast, we wanted to avoid using Broadcasts

## Concept
Speaking about scanning qr-codes, lets shortly address the process a user has to go through
in order to establish a connection to another device.
Let's call our users Daniel and Anika.
Anika clicks a button in her app, which makes a qr-code appear on her screen:

![QR-Code Offer](./qr_offer.png)

Daniel in return clicks a second button on his phone, which respectively opens a scanner:

![QR-Code Scanner](./qr_scanner.png).

After the scanner has successfully scanned the code, the phones automatically exchange information like the username, 
the mac-address, the link-local-address if possible, the IPv4 address as a fallback, making Daniel appear in Annikas contact list and vice versa.

Finally, when one clicks the name of the other contact, the app tries to reach that contact through the saved address.
In case of failure the app tries to mutate that address considering its own network prefix and the mac-address of the target.
Is this connection established, a handshake is transmitted and finally a call is established using the WebRTC-PeerConnection etc. classes.

## WebRTC
[WebRTC](https://en.wikipedia.org/wiki/WebRTC) is a standard for audio and videocommunication supported by many major browsers and platforms, allowing the development of VoIP applications without the need of additional plugins.
It starts with a signalling part where the connecting nodes try to find each other/find the best way to reach each other.
This is often done using STUN/TURN servers, which are a part of WebRTC aswell.
Those servers are used to determine the external IP address of a node, as well to relay traffic if NATs or similar disallow a direct connection.
The libraries and APIs provided by WebRTC also handle the audio and video recording as well as encoding tha data in the most efficient way possible.
During the handshake, the nodes exchange accepted encoding formats, so that they know which encoding format best to use.
All the application has to do is to transmit the handshake in both directions, once a connection is established WebRTC does the rest.
WebRTC plays a huge role in the project, since one of the goals is to demonstrate a serverless use of WebRTC, 
basically establishing a connection between two devices without using a centralized server instance.
The reason this gets its own paragraph is the fact that all the major apps that utilize WebRTC use a centralized signalling server,
thus most of the available tutorials on WebRTC include such a server as well.

Such a server basically acts as a messenger between nodes, delivering handshake-messages between the devices
as well as trying to establish a direct connection between these devices.
Since our project is meant to be used mainly in closed networks and the nodes know every contacts address, there no need for such a server.

We hope our project could firstly act as proof of such a possibility, secondly as a motivation and maybe as a tutorial to similar projects.

## some words about WebRTC and IPv6
We decided to use WebRTC since it is a well-build, tested and mostly documented standard for video- and audio communication,
which is exactly what we needed.
Since WebRTC is used in many different mobile and web applications we have room for scalability, giving us the possibility to adapt to other projects and applications.
The signalling, e.g. the exchange of relevant networking information is done in the application,
but when the WebRTC-layer is given an appropriate communication channel (in this case an already connected Socket) it handles everything else required, including data-channels that can be used for e.g. file exchange, messaging, service messages etc.

Firstly, we decided to use IPv6, since every device capable of IPv6 has its own link-local address ("fe80:..."), thus there is no need for a DHCP server. Additionaly, this link local address does almost never change, so the devices stay reachable even if the network is changed.
IPv4 addresses often change, so even in the same network a node may get unreachable when its address changes.
Since the address space of IPv6 is several times bigger, those addresses normally don't change.
Secondly, an IPv6-address is build around the mac-address of the device.
The best example for such a case is the link-local address of the device, which we try to exchange at first.
Parallel to the link local address, in the Freifunk network addresses are build following EUI64, which means that the mac address of a device is wrapped with a fixed prefix.
Privacy extensions may break the whole idea of having the mac address of a device in the IPv6 address, since their job is to shuffle that address, making it harder to track a certain device. Since privacy extensions are not enabled on Android by dewfault, this should not get an issue.
If no IPv6 is available at all, the IPv4 address is used as a fallback.
Of course, all of that only work when the devices connect and communicate without switching the network.
In another networks the devices may get assigned another address, which they would have to exchange once again.
To work around that issue, the application contains several algorithms that try to determine whether the device's local address contains its own mac address.
If this is the case the application filters the network-prefix out of the IPv6 address currently assigned to the device, and builds the target address using the network-prefix and the target mac.
Additionally, a device may have a global address, making it reachable from the outer world, although we did yet not focus on that topic.
A connection payload, sent to pair one device with another one may look like this:
{"action":"connect","username":"thisIsATestUser","identifier":"11:22:33:44:55:66","address":"fe80::1322:33ff:fe44:5566"}.


All in all, Meshenger provides an audio and video communication application applicable for local networks like fairs, conventions etc.