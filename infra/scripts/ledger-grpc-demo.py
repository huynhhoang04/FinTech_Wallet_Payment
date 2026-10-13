#!/usr/bin/env python3
"""Chay 8 RPC cua ledger.v1.LedgerService cho phan bao ve gRPC - khong can protoc, khong can Bruno.

  python3 ledger-grpc-demo.py                      # 5 RPC chi doc (an toan)
  python3 ledger-grpc-demo.py --rpc GetBalance     # chay 1 RPC
  python3 ledger-grpc-demo.py --posting            # them 3 RPC dong tien (tu sinh transactionId moi)
  python3 ledger-grpc-demo.py --host 127.0.0.1:9090 --account b2d25d75-...

Moi request duoc encode tay theo proto (grpcio truyen bytes tho qua serializer), nen khong can
sinh stub. Ket qua duoc decode theo schema ben duoi. Chay tu may co tunnel (bruno-tunnel.sh) hoac
tren VPS (--host 172.18.0.x:9090 cua container account-ledger-service).
"""
import argparse
import sys
import uuid

import grpc

# ---------------------------------------------------------------- protobuf encode
def varint(value):
    out = b""
    while True:
        byte = value & 0x7F
        value >>= 7
        out += bytes([byte | (0x80 if value else 0)])
        if not value:
            return out


def str_field(number, text):
    raw = str(text).encode()
    return varint(number << 3 | 2) + varint(len(raw)) + raw


def int_field(number, value):
    return varint(number << 3 | 0) + varint(value)


def msg_field(number, body):
    return varint(number << 3 | 2) + varint(len(body)) + body


def money(amount, currency="VND"):
    return int_field(1, amount) + str_field(2, currency)


# ---------------------------------------------------------------- protobuf decode
def parse(data):
    """Tra ve list [(field_number, wire_type, value)] - du de doc cac message o day."""
    fields, i = [], 0
    while i < len(data):
        tag = shift = 0
        while True:
            b = data[i]; i += 1
            tag |= (b & 0x7F) << shift; shift += 7
            if not b & 0x80:
                break
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value = shift = 0
            while True:
                b = data[i]; i += 1
                value |= (b & 0x7F) << shift; shift += 7
                if not b & 0x80:
                    break
            # int64 am duoc encode thanh varint 10 byte; doi ve so co dau cho de doc
            if value > 0x7FFFFFFFFFFFFFFF:
                value -= 1 << 64
            fields.append((number, wire, value))
        elif wire == 2:
            length = shift = 0
            while True:
                b = data[i]; i += 1
                length |= (b & 0x7F) << shift; shift += 7
                if not b & 0x80:
                    break
            raw = data[i:i + length]; i += length
            fields.append((number, wire, raw))
        else:
            raise SystemExit(f"wire type {wire} khong ho tro")
    return fields


# schema: message -> {field_number: (ten, kieu)}   kieu: str | int | msg | rep
SCHEMAS = {
    "GetBalanceResponse": {1: ("accountId", "str"), 2: ("currency", "str"),
                           3: ("available", "int"), 4: ("pending", "int")},
    "AccountSummary": {1: ("accountId", "str"), 2: ("accountNumber", "str"),
                       3: ("currency", "str"), 4: ("status", "str")},
    "AccountList": {1: ("accounts", "rep")},
    "LedgerEntryDto": {1: ("entryId", "str"), 2: ("amountSigned", "int"), 3: ("direction", "str"),
                       4: ("balanceAfter", "int"), 5: ("atEpochMs", "int"), 6: ("accountId", "str")},
    "GetStatementResponse": {1: ("entries", "rep"), 2: ("openingBalance", "int"),
                             3: ("closingBalance", "int")},
    "GetLedgerEntriesResponse": {1: ("entries", "rep"), 2: ("sum", "int")},
    "PostTransferResponse": {1: ("ledgerTransactionId", "str"), 2: ("status", "str"),
                             3: ("sourceBalanceAfter", "int"), 4: ("targetBalanceAfter", "int"),
                             5: ("errorCode", "str")},
    "LedgerResult": {1: ("ledgerTransactionId", "str"), 2: ("status", "str"),
                     3: ("balanceAfter", "int"), 4: ("errorCode", "str")},
}
NESTED = {"rep": ["AccountSummary", "LedgerEntryDto"]}


def decode(data, schema):
    spec = SCHEMAS.get(schema, {})
    out = {}
    for number, wire, value in parse(data):
        name, kind = spec.get(number, (f"field{number}", "int" if wire == 0 else "str"))
        if kind == "str":
            value = value.decode("utf-8", "replace") if isinstance(value, bytes) else value
        elif kind == "rep":
            child = NESTED["rep"][0] if schema == "AccountList" else "LedgerEntryDto"
            out.setdefault(name, []).append(decode(value, child))
            continue
        out[name] = value
    # giu output gon: chi in 5 phan tu dau cua list dai
    for key, val in list(out.items()):
        if isinstance(val, list) and len(val) > 5:
            out[key] = val[:5] + [f"... ({len(val) - 5} more)"]
    return out


# ---------------------------------------------------------------- requests
def build(account, source, target, target_number, owner, transaction_id, ledger_transaction_id, amount):
    return {
        "GetBalance": ("GetBalanceRequest", str_field(1, account), "GetBalanceResponse"),
        "GetAccountByNumber": ("GetAccountByNumberRequest", str_field(1, target_number), "AccountSummary"),
        "GetAccountsByOwner": ("GetAccountsByOwnerRequest", str_field(1, owner), "AccountList"),
        "GetStatement": ("GetStatementRequest", str_field(1, account) + int_field(4, 0) + int_field(5, 20),
                         "GetStatementResponse"),
        "GetLedgerEntries": ("GetLedgerEntriesRequest", str_field(1, ledger_transaction_id),
                             "GetLedgerEntriesResponse"),
        "PostTransfer": ("PostTransferRequest",
                         str_field(1, transaction_id) + str_field(2, source) + str_field(3, target)
                         + msg_field(4, money(amount)) + str_field(5, "bruno-demo transfer"),
                         "PostTransferResponse"),
        "Withdraw": ("WithdrawRequest",
                     str_field(1, transaction_id) + str_field(2, account) + msg_field(3, money(amount))
                     + str_field(4, "grpc-demo"),
                     "LedgerResult"),
        "Deposit": ("DepositRequest",
                    str_field(1, transaction_id) + str_field(2, account) + msg_field(3, money(amount))
                    + str_field(4, "grpc-demo"),
                    "LedgerResult"),
    }


READ_ONLY = ["GetBalance", "GetAccountByNumber", "GetAccountsByOwner", "GetStatement", "GetLedgerEntries"]
POSTING = ["PostTransfer", "Withdraw", "Deposit"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1:9090")
    ap.add_argument("--account", default="b2d25d75-05dd-478d-bd6d-7a43b6c6e7f4")
    ap.add_argument("--source", default="b2d25d75-05dd-478d-bd6d-7a43b6c6e7f4")
    ap.add_argument("--target", default="28af8cb8-24a8-4d2a-bab0-fe333cbc2ffc")
    ap.add_argument("--target-number", default="9704524581836")
    ap.add_argument("--owner", default="71308037-327b-4fe2-b252-2efc6f8754fb")
    ap.add_argument("--ledger-transaction-id", default="70d69993-facf-3705-810f-3b063301383b")
    ap.add_argument("--amount", type=int, default=1)
    ap.add_argument("--rpc", help="chi chay 1 RPC")
    ap.add_argument("--posting", action="store_true", help="chay ca 3 RPC dong tien (tu sinh transactionId moi)")
    args = ap.parse_args()

    channel = grpc.insecure_channel(args.host)
    plan = [args.rpc] if args.rpc else (READ_ONLY + (POSTING if args.posting else []))
    unknown = [r for r in plan if r not in READ_ONLY + POSTING]
    if unknown:
        raise SystemExit(f"RPC khong biet: {unknown}. Chon trong {READ_ONLY + POSTING}")

    print(f"# ledger.v1.LedgerService @ {args.host}\n")
    requests = build(args.account, args.source, args.target, args.target_number,
                     args.owner, "", args.ledger_transaction_id, args.amount)
    for rpc in plan:
        # posting phai dung transaction id MOI moi lan, neu khong se la replay (dung thiet ke idempotent)
        tx = str(uuid.uuid4()) if rpc in POSTING else args.ledger_transaction_id
        _, payload, response_type = build(args.account, args.source, args.target, args.target_number,
                                          args.owner, tx, args.ledger_transaction_id, args.amount)[rpc]
        method = channel.unary_unary(f"/ledger.v1.LedgerService/{rpc}",
                                     request_serializer=lambda b: b, response_deserializer=lambda b: b)
        try:
            raw = method(payload, timeout=10)
            print(f"{rpc:20s} OK   {decode(raw, response_type)}")
        except grpc.RpcError as exc:
            print(f"{rpc:20s} FAIL {exc.code()} {exc.details()}")
    if args.posting:
        print("\n# PostTransfer/Withdraw/Deposit da doi tien that (replay theo transactionId la no-op)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
