#!/usr/bin/env python3
"""Runs commands on a Minecraft server over RCON and checks their answers.

    python3 tools/rcon.py "slum version" "slum structure place resident_house => moved in"

Each argument is a command, optionally followed by ` => text` the reply must contain. Prints
every reply; exits with the number of commands whose reply was missing its text. The host,
port and password come from RCON_HOST, RCON_PORT and RCON_PASSWORD (defaults: localhost,
25575, and `slumdrugs`). No dependencies: the protocol is a length, an id, a type and a body.
"""
import os
import socket
import struct
import sys

SERVERDATA_AUTH, SERVERDATA_EXECCOMMAND = 3, 2


def packet(pid, kind, body):
    payload = struct.pack('<ii', pid, kind) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(payload)) + payload


def receive(sock):
    head = sock.recv(4)
    if len(head) < 4:
        raise ConnectionError('server closed the connection')
    (length,) = struct.unpack('<i', head)
    data = b''
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError('server closed the connection')
        data += chunk
    pid, kind = struct.unpack('<ii', data[:8])
    return pid, kind, data[8:-2].decode('utf-8', 'replace')


def connect(host, port, password):
    sock = socket.create_connection((host, port), timeout=20)
    sock.sendall(packet(1, SERVERDATA_AUTH, password))
    pid, kind, _ = receive(sock)
    if pid == -1:
        raise PermissionError('RCON password refused')
    return sock


def run(sock, command, pid=7):
    sock.sendall(packet(pid, SERVERDATA_EXECCOMMAND, command))
    _, _, body = receive(sock)
    return body


def main(args):
    host = os.environ.get('RCON_HOST', 'localhost')
    port = int(os.environ.get('RCON_PORT', '25575'))
    password = os.environ.get('RCON_PASSWORD', 'slumdrugs')
    sock = connect(host, port, password)
    failures = 0
    for i, arg in enumerate(args):
        command, _, expect = arg.partition(' => ')
        # A line "sleep N" waits N seconds here, for chunks a forceload is still bringing in.
        if command.strip().startswith('sleep '):
            import time
            time.sleep(float(command.split()[1]))
            print('     (slept ' + command.split()[1] + ' s)')
            continue
        reply = run(sock, command.strip(), 10 + i)
        ok = expect.strip() in reply if expect else True
        print(('ok   ' if ok else 'FAIL ') + '/' + command.strip() + '\n     ' + reply.replace('\n', '\n     '))
        if not ok:
            failures += 1
    sock.close()
    sys.exit(failures)


if __name__ == '__main__':
    main(sys.argv[1:])
