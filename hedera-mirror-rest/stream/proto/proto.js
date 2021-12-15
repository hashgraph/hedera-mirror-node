'use strict';
var $protobuf = _interopRequireWildcard(require('protobufjs/minimal'));
Object.defineProperty(exports, '__esModule', {value: !0}), (exports.default = exports.google = exports.proto = void 0);
function _getRequireWildcardCache(e) {
  if ('function' != typeof WeakMap) return null;
  var o = new WeakMap(),
    t = new WeakMap();
  return (_getRequireWildcardCache = function (e) {
    return e ? t : o;
  })(e);
}
function _interopRequireWildcard(e, o) {
  if (!o && e && e.__esModule) return e;
  if (null === e || ('object' != typeof e && 'function' != typeof e)) return {default: e};
  var t = _getRequireWildcardCache(o);
  if (t && t.has(e)) return t.get(e);
  var n = {},
    r = Object.defineProperty && Object.getOwnPropertyDescriptor;
  for (var i in e)
    if ('default' != i && Object.prototype.hasOwnProperty.call(e, i)) {
      var d = r ? Object.getOwnPropertyDescriptor(e, i) : null;
      d && (d.get || d.set) ? Object.defineProperty(n, i, d) : (n[i] = e[i]);
    }
  return (n.default = e), t && t.set(e, n), n;
}
const $Reader = $protobuf.Reader,
  $Writer = $protobuf.Writer,
  $util = $protobuf.util,
  $root = $protobuf.roots.hashgraph || ($protobuf.roots.hashgraph = {});
exports.default = $root;
const proto = ($root.proto = (() => {
  const e = {
    TokenUnitBalance: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenId = null),
        (e.prototype.balance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.tokenId &&
              Object.hasOwnProperty.call(e, 'tokenId') &&
              $root.proto.TokenID.encode(e.tokenId, o.uint32(10).fork()).ldelim(),
            null != e.balance && Object.hasOwnProperty.call(e, 'balance') && o.uint32(16).uint64(e.balance),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenUnitBalance(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.tokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.balance = e.uint64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SingleAccountBalances: (function () {
      function e(e) {
        if (((this.tokenUnitBalances = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.accountID = null),
        (e.prototype.hbarBalance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.tokenUnitBalances = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(10).fork()).ldelim(),
            null != e.hbarBalance && Object.hasOwnProperty.call(e, 'hbarBalance') && o.uint32(16).uint64(e.hbarBalance),
            null != e.tokenUnitBalances && e.tokenUnitBalances.length)
          )
            for (var t = 0; t < e.tokenUnitBalances.length; ++t)
              $root.proto.TokenUnitBalance.encode(e.tokenUnitBalances[t], o.uint32(26).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SingleAccountBalances(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.hbarBalance = e.uint64();
                break;
              case 3:
                (i.tokenUnitBalances && i.tokenUnitBalances.length) || (i.tokenUnitBalances = []),
                  i.tokenUnitBalances.push($root.proto.TokenUnitBalance.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    AllAccountBalances: (function () {
      function e(e) {
        if (((this.allAccounts = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.consensusTimestamp = null),
        (e.prototype.allAccounts = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.consensusTimestamp &&
              Object.hasOwnProperty.call(e, 'consensusTimestamp') &&
              $root.proto.Timestamp.encode(e.consensusTimestamp, o.uint32(10).fork()).ldelim(),
            null != e.allAccounts && e.allAccounts.length)
          )
            for (var t = 0; t < e.allAccounts.length; ++t)
              $root.proto.SingleAccountBalances.encode(e.allAccounts[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.AllAccountBalances(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.consensusTimestamp = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 2:
                (i.allAccounts && i.allAccounts.length) || (i.allAccounts = []),
                  i.allAccounts.push($root.proto.SingleAccountBalances.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ShardID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ShardID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    RealmID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.realmNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            null != e.realmNum && Object.hasOwnProperty.call(e, 'realmNum') && o.uint32(16).int64(e.realmNum),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.RealmID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              case 2:
                i.realmNum = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    AccountID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.realmNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.accountNum = null),
        (e.prototype.alias = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'account', {
          get: $util.oneOfGetter((o = ['accountNum', 'alias'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            null != e.realmNum && Object.hasOwnProperty.call(e, 'realmNum') && o.uint32(16).int64(e.realmNum),
            null != e.accountNum && Object.hasOwnProperty.call(e, 'accountNum') && o.uint32(24).int64(e.accountNum),
            null != e.alias && Object.hasOwnProperty.call(e, 'alias') && o.uint32(34).bytes(e.alias),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.AccountID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              case 2:
                i.realmNum = e.int64();
                break;
              case 3:
                i.accountNum = e.int64();
                break;
              case 4:
                i.alias = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.realmNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.fileNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            null != e.realmNum && Object.hasOwnProperty.call(e, 'realmNum') && o.uint32(16).int64(e.realmNum),
            null != e.fileNum && Object.hasOwnProperty.call(e, 'fileNum') && o.uint32(24).int64(e.fileNum),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              case 2:
                i.realmNum = e.int64();
                break;
              case 3:
                i.fileNum = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.realmNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.contractNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            null != e.realmNum && Object.hasOwnProperty.call(e, 'realmNum') && o.uint32(16).int64(e.realmNum),
            null != e.contractNum && Object.hasOwnProperty.call(e, 'contractNum') && o.uint32(24).int64(e.contractNum),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              case 2:
                i.realmNum = e.int64();
                break;
              case 3:
                i.contractNum = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.transactionValidStart = null),
        (e.prototype.accountID = null),
        (e.prototype.scheduled = !1),
        (e.prototype.nonce = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.transactionValidStart &&
              Object.hasOwnProperty.call(e, 'transactionValidStart') &&
              $root.proto.Timestamp.encode(e.transactionValidStart, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.scheduled && Object.hasOwnProperty.call(e, 'scheduled') && o.uint32(24).bool(e.scheduled),
            null != e.nonce && Object.hasOwnProperty.call(e, 'nonce') && o.uint32(32).int32(e.nonce),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.transactionValidStart = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.scheduled = e.bool();
                break;
              case 4:
                i.nonce = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    AccountAmount: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.accountID = null),
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(10).fork()).ldelim(),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(16).sint64(e.amount),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.AccountAmount(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.amount = e.sint64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransferList: (function () {
      function e(e) {
        if (((this.accountAmounts = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.accountAmounts = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.accountAmounts && e.accountAmounts.length))
            for (var t = 0; t < e.accountAmounts.length; ++t)
              $root.proto.AccountAmount.encode(e.accountAmounts[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransferList(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.accountAmounts && i.accountAmounts.length) || (i.accountAmounts = []),
                  i.accountAmounts.push($root.proto.AccountAmount.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NftTransfer: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.senderAccountID = null),
        (e.prototype.receiverAccountID = null),
        (e.prototype.serialNumber = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.senderAccountID &&
              Object.hasOwnProperty.call(e, 'senderAccountID') &&
              $root.proto.AccountID.encode(e.senderAccountID, o.uint32(10).fork()).ldelim(),
            null != e.receiverAccountID &&
              Object.hasOwnProperty.call(e, 'receiverAccountID') &&
              $root.proto.AccountID.encode(e.receiverAccountID, o.uint32(18).fork()).ldelim(),
            null != e.serialNumber &&
              Object.hasOwnProperty.call(e, 'serialNumber') &&
              o.uint32(24).int64(e.serialNumber),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NftTransfer(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.senderAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.receiverAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.serialNumber = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenTransferList: (function () {
      function e(e) {
        if (((this.transfers = []), (this.nftTransfers = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.transfers = $util.emptyArray),
        (e.prototype.nftTransfers = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.transfers && e.transfers.length)
          )
            for (var t = 0; t < e.transfers.length; ++t)
              $root.proto.AccountAmount.encode(e.transfers[t], o.uint32(18).fork()).ldelim();
          if (null != e.nftTransfers && e.nftTransfers.length)
            for (var t = 0; t < e.nftTransfers.length; ++t)
              $root.proto.NftTransfer.encode(e.nftTransfers[t], o.uint32(26).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenTransferList(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                (i.transfers && i.transfers.length) || (i.transfers = []),
                  i.transfers.push($root.proto.AccountAmount.decode(e, e.uint32()));
                break;
              case 3:
                (i.nftTransfers && i.nftTransfers.length) || (i.nftTransfers = []),
                  i.nftTransfers.push($root.proto.NftTransfer.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Fraction: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.numerator = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.denominator = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.numerator && Object.hasOwnProperty.call(e, 'numerator') && o.uint32(8).int64(e.numerator),
            null != e.denominator && Object.hasOwnProperty.call(e, 'denominator') && o.uint32(16).int64(e.denominator),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Fraction(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.numerator = e.int64();
                break;
              case 2:
                i.denominator = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TopicID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.realmNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.topicNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            null != e.realmNum && Object.hasOwnProperty.call(e, 'realmNum') && o.uint32(16).int64(e.realmNum),
            null != e.topicNum && Object.hasOwnProperty.call(e, 'topicNum') && o.uint32(24).int64(e.topicNum),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TopicID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              case 2:
                i.realmNum = e.int64();
                break;
              case 3:
                i.topicNum = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.realmNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.tokenNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            null != e.realmNum && Object.hasOwnProperty.call(e, 'realmNum') && o.uint32(16).int64(e.realmNum),
            null != e.tokenNum && Object.hasOwnProperty.call(e, 'tokenNum') && o.uint32(24).int64(e.tokenNum),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              case 2:
                i.realmNum = e.int64();
                break;
              case 3:
                i.tokenNum = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ScheduleID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.shardNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.realmNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.scheduleNum = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.shardNum && Object.hasOwnProperty.call(e, 'shardNum') && o.uint32(8).int64(e.shardNum),
            null != e.realmNum && Object.hasOwnProperty.call(e, 'realmNum') && o.uint32(16).int64(e.realmNum),
            null != e.scheduleNum && Object.hasOwnProperty.call(e, 'scheduleNum') && o.uint32(24).int64(e.scheduleNum),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ScheduleID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.shardNum = e.int64();
                break;
              case 2:
                i.realmNum = e.int64();
                break;
              case 3:
                i.scheduleNum = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenType: (function () {
      const e = {},
        o = Object.create(e);
      return (o[(e[0] = 'FUNGIBLE_COMMON')] = 0), (o[(e[1] = 'NON_FUNGIBLE_UNIQUE')] = 1), o;
    })(),
    SubType: (function () {
      const e = {},
        o = Object.create(e);
      return (
        (o[(e[0] = 'DEFAULT')] = 0),
        (o[(e[1] = 'TOKEN_FUNGIBLE_COMMON')] = 1),
        (o[(e[2] = 'TOKEN_NON_FUNGIBLE_UNIQUE')] = 2),
        (o[(e[3] = 'TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES')] = 3),
        (o[(e[4] = 'TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES')] = 4),
        o
      );
    })(),
    TokenSupplyType: (function () {
      const e = {},
        o = Object.create(e);
      return (o[(e[0] = 'INFINITE')] = 0), (o[(e[1] = 'FINITE')] = 1), o;
    })(),
    TokenFreezeStatus: (function () {
      const e = {},
        o = Object.create(e);
      return (o[(e[0] = 'FreezeNotApplicable')] = 0), (o[(e[1] = 'Frozen')] = 1), (o[(e[2] = 'Unfrozen')] = 2), o;
    })(),
    TokenKycStatus: (function () {
      const e = {},
        o = Object.create(e);
      return (o[(e[0] = 'KycNotApplicable')] = 0), (o[(e[1] = 'Granted')] = 1), (o[(e[2] = 'Revoked')] = 2), o;
    })(),
    TokenPauseStatus: (function () {
      const e = {},
        o = Object.create(e);
      return (o[(e[0] = 'PauseNotApplicable')] = 0), (o[(e[1] = 'Paused')] = 1), (o[(e[2] = 'Unpaused')] = 2), o;
    })(),
    Key: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.contractID = null),
        (e.prototype.ed25519 = null),
        (e.prototype.RSA_3072 = null),
        (e.prototype.ECDSA_384 = null),
        (e.prototype.thresholdKey = null),
        (e.prototype.keyList = null),
        (e.prototype.ECDSASecp256k1 = null),
        (e.prototype.delegatableContractId = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'key', {
          get: $util.oneOfGetter(
            (o = [
              'contractID',
              'ed25519',
              'RSA_3072',
              'ECDSA_384',
              'thresholdKey',
              'keyList',
              'ECDSASecp256k1',
              'delegatableContractId',
            ])
          ),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(10).fork()).ldelim(),
            null != e.ed25519 && Object.hasOwnProperty.call(e, 'ed25519') && o.uint32(18).bytes(e.ed25519),
            null != e.RSA_3072 && Object.hasOwnProperty.call(e, 'RSA_3072') && o.uint32(26).bytes(e.RSA_3072),
            null != e.ECDSA_384 && Object.hasOwnProperty.call(e, 'ECDSA_384') && o.uint32(34).bytes(e.ECDSA_384),
            null != e.thresholdKey &&
              Object.hasOwnProperty.call(e, 'thresholdKey') &&
              $root.proto.ThresholdKey.encode(e.thresholdKey, o.uint32(42).fork()).ldelim(),
            null != e.keyList &&
              Object.hasOwnProperty.call(e, 'keyList') &&
              $root.proto.KeyList.encode(e.keyList, o.uint32(50).fork()).ldelim(),
            null != e.ECDSASecp256k1 &&
              Object.hasOwnProperty.call(e, 'ECDSASecp256k1') &&
              o.uint32(58).bytes(e.ECDSASecp256k1),
            null != e.delegatableContractId &&
              Object.hasOwnProperty.call(e, 'delegatableContractId') &&
              $root.proto.ContractID.encode(e.delegatableContractId, o.uint32(66).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Key(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 2:
                i.ed25519 = e.bytes();
                break;
              case 3:
                i.RSA_3072 = e.bytes();
                break;
              case 4:
                i.ECDSA_384 = e.bytes();
                break;
              case 5:
                i.thresholdKey = $root.proto.ThresholdKey.decode(e, e.uint32());
                break;
              case 6:
                i.keyList = $root.proto.KeyList.decode(e, e.uint32());
                break;
              case 7:
                i.ECDSASecp256k1 = e.bytes();
                break;
              case 8:
                i.delegatableContractId = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ThresholdKey: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.threshold = 0),
        (e.prototype.keys = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.threshold && Object.hasOwnProperty.call(e, 'threshold') && o.uint32(8).uint32(e.threshold),
            null != e.keys &&
              Object.hasOwnProperty.call(e, 'keys') &&
              $root.proto.KeyList.encode(e.keys, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ThresholdKey(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.threshold = e.uint32();
                break;
              case 2:
                i.keys = $root.proto.KeyList.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    KeyList: (function () {
      function e(e) {
        if (((this.keys = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.keys = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.keys && e.keys.length))
            for (var t = 0; t < e.keys.length; ++t) $root.proto.Key.encode(e.keys[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.KeyList(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.keys && i.keys.length) || (i.keys = []), i.keys.push($root.proto.Key.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Signature: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.contract = null),
        (e.prototype.ed25519 = null),
        (e.prototype.RSA_3072 = null),
        (e.prototype.ECDSA_384 = null),
        (e.prototype.thresholdSignature = null),
        (e.prototype.signatureList = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'signature', {
          get: $util.oneOfGetter(
            (o = ['contract', 'ed25519', 'RSA_3072', 'ECDSA_384', 'thresholdSignature', 'signatureList'])
          ),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.contract && Object.hasOwnProperty.call(e, 'contract') && o.uint32(10).bytes(e.contract),
            null != e.ed25519 && Object.hasOwnProperty.call(e, 'ed25519') && o.uint32(18).bytes(e.ed25519),
            null != e.RSA_3072 && Object.hasOwnProperty.call(e, 'RSA_3072') && o.uint32(26).bytes(e.RSA_3072),
            null != e.ECDSA_384 && Object.hasOwnProperty.call(e, 'ECDSA_384') && o.uint32(34).bytes(e.ECDSA_384),
            null != e.thresholdSignature &&
              Object.hasOwnProperty.call(e, 'thresholdSignature') &&
              $root.proto.ThresholdSignature.encode(e.thresholdSignature, o.uint32(42).fork()).ldelim(),
            null != e.signatureList &&
              Object.hasOwnProperty.call(e, 'signatureList') &&
              $root.proto.SignatureList.encode(e.signatureList, o.uint32(50).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Signature(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.contract = e.bytes();
                break;
              case 2:
                i.ed25519 = e.bytes();
                break;
              case 3:
                i.RSA_3072 = e.bytes();
                break;
              case 4:
                i.ECDSA_384 = e.bytes();
                break;
              case 5:
                i.thresholdSignature = $root.proto.ThresholdSignature.decode(e, e.uint32());
                break;
              case 6:
                i.signatureList = $root.proto.SignatureList.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ThresholdSignature: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.sigs = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.sigs &&
              Object.hasOwnProperty.call(e, 'sigs') &&
              $root.proto.SignatureList.encode(e.sigs, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ThresholdSignature(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 2:
                i.sigs = $root.proto.SignatureList.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SignatureList: (function () {
      function e(e) {
        if (((this.sigs = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.sigs = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.sigs && e.sigs.length))
            for (var t = 0; t < e.sigs.length; ++t)
              $root.proto.Signature.encode(e.sigs[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SignatureList(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 2:
                (i.sigs && i.sigs.length) || (i.sigs = []), i.sigs.push($root.proto.Signature.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SignaturePair: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.pubKeyPrefix = $util.newBuffer([])),
        (e.prototype.contract = null),
        (e.prototype.ed25519 = null),
        (e.prototype.RSA_3072 = null),
        (e.prototype.ECDSA_384 = null),
        (e.prototype.ECDSASecp256k1 = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'signature', {
          get: $util.oneOfGetter((o = ['contract', 'ed25519', 'RSA_3072', 'ECDSA_384', 'ECDSASecp256k1'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.pubKeyPrefix &&
              Object.hasOwnProperty.call(e, 'pubKeyPrefix') &&
              o.uint32(10).bytes(e.pubKeyPrefix),
            null != e.contract && Object.hasOwnProperty.call(e, 'contract') && o.uint32(18).bytes(e.contract),
            null != e.ed25519 && Object.hasOwnProperty.call(e, 'ed25519') && o.uint32(26).bytes(e.ed25519),
            null != e.RSA_3072 && Object.hasOwnProperty.call(e, 'RSA_3072') && o.uint32(34).bytes(e.RSA_3072),
            null != e.ECDSA_384 && Object.hasOwnProperty.call(e, 'ECDSA_384') && o.uint32(42).bytes(e.ECDSA_384),
            null != e.ECDSASecp256k1 &&
              Object.hasOwnProperty.call(e, 'ECDSASecp256k1') &&
              o.uint32(50).bytes(e.ECDSASecp256k1),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SignaturePair(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.pubKeyPrefix = e.bytes();
                break;
              case 2:
                i.contract = e.bytes();
                break;
              case 3:
                i.ed25519 = e.bytes();
                break;
              case 4:
                i.RSA_3072 = e.bytes();
                break;
              case 5:
                i.ECDSA_384 = e.bytes();
                break;
              case 6:
                i.ECDSASecp256k1 = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SignatureMap: (function () {
      function e(e) {
        if (((this.sigPair = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.sigPair = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.sigPair && e.sigPair.length))
            for (var t = 0; t < e.sigPair.length; ++t)
              $root.proto.SignaturePair.encode(e.sigPair[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SignatureMap(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.sigPair && i.sigPair.length) || (i.sigPair = []),
                  i.sigPair.push($root.proto.SignaturePair.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    HederaFunctionality: (function () {
      const e = {},
        o = Object.create(e);
      return (
        (o[(e[0] = 'NONE')] = 0),
        (o[(e[1] = 'CryptoTransfer')] = 1),
        (o[(e[2] = 'CryptoUpdate')] = 2),
        (o[(e[3] = 'CryptoDelete')] = 3),
        (o[(e[4] = 'CryptoAddLiveHash')] = 4),
        (o[(e[5] = 'CryptoDeleteLiveHash')] = 5),
        (o[(e[6] = 'ContractCall')] = 6),
        (o[(e[7] = 'ContractCreate')] = 7),
        (o[(e[8] = 'ContractUpdate')] = 8),
        (o[(e[9] = 'FileCreate')] = 9),
        (o[(e[10] = 'FileAppend')] = 10),
        (o[(e[11] = 'FileUpdate')] = 11),
        (o[(e[12] = 'FileDelete')] = 12),
        (o[(e[13] = 'CryptoGetAccountBalance')] = 13),
        (o[(e[14] = 'CryptoGetAccountRecords')] = 14),
        (o[(e[15] = 'CryptoGetInfo')] = 15),
        (o[(e[16] = 'ContractCallLocal')] = 16),
        (o[(e[17] = 'ContractGetInfo')] = 17),
        (o[(e[18] = 'ContractGetBytecode')] = 18),
        (o[(e[19] = 'GetBySolidityID')] = 19),
        (o[(e[20] = 'GetByKey')] = 20),
        (o[(e[21] = 'CryptoGetLiveHash')] = 21),
        (o[(e[22] = 'CryptoGetStakers')] = 22),
        (o[(e[23] = 'FileGetContents')] = 23),
        (o[(e[24] = 'FileGetInfo')] = 24),
        (o[(e[25] = 'TransactionGetRecord')] = 25),
        (o[(e[26] = 'ContractGetRecords')] = 26),
        (o[(e[27] = 'CryptoCreate')] = 27),
        (o[(e[28] = 'SystemDelete')] = 28),
        (o[(e[29] = 'SystemUndelete')] = 29),
        (o[(e[30] = 'ContractDelete')] = 30),
        (o[(e[31] = 'Freeze')] = 31),
        (o[(e[32] = 'CreateTransactionRecord')] = 32),
        (o[(e[33] = 'CryptoAccountAutoRenew')] = 33),
        (o[(e[34] = 'ContractAutoRenew')] = 34),
        (o[(e[35] = 'GetVersionInfo')] = 35),
        (o[(e[36] = 'TransactionGetReceipt')] = 36),
        (o[(e[50] = 'ConsensusCreateTopic')] = 50),
        (o[(e[51] = 'ConsensusUpdateTopic')] = 51),
        (o[(e[52] = 'ConsensusDeleteTopic')] = 52),
        (o[(e[53] = 'ConsensusGetTopicInfo')] = 53),
        (o[(e[54] = 'ConsensusSubmitMessage')] = 54),
        (o[(e[55] = 'UncheckedSubmit')] = 55),
        (o[(e[56] = 'TokenCreate')] = 56),
        (o[(e[58] = 'TokenGetInfo')] = 58),
        (o[(e[59] = 'TokenFreezeAccount')] = 59),
        (o[(e[60] = 'TokenUnfreezeAccount')] = 60),
        (o[(e[61] = 'TokenGrantKycToAccount')] = 61),
        (o[(e[62] = 'TokenRevokeKycFromAccount')] = 62),
        (o[(e[63] = 'TokenDelete')] = 63),
        (o[(e[64] = 'TokenUpdate')] = 64),
        (o[(e[65] = 'TokenMint')] = 65),
        (o[(e[66] = 'TokenBurn')] = 66),
        (o[(e[67] = 'TokenAccountWipe')] = 67),
        (o[(e[68] = 'TokenAssociateToAccount')] = 68),
        (o[(e[69] = 'TokenDissociateFromAccount')] = 69),
        (o[(e[70] = 'ScheduleCreate')] = 70),
        (o[(e[71] = 'ScheduleDelete')] = 71),
        (o[(e[72] = 'ScheduleSign')] = 72),
        (o[(e[73] = 'ScheduleGetInfo')] = 73),
        (o[(e[74] = 'TokenGetAccountNftInfos')] = 74),
        (o[(e[75] = 'TokenGetNftInfo')] = 75),
        (o[(e[76] = 'TokenGetNftInfos')] = 76),
        (o[(e[77] = 'TokenFeeScheduleUpdate')] = 77),
        (o[(e[78] = 'NetworkGetExecutionTime')] = 78),
        (o[(e[79] = 'TokenPause')] = 79),
        (o[(e[80] = 'TokenUnpause')] = 80),
        o
      );
    })(),
    FeeComponents: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.min = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.max = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.constant = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.bpt = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.vpt = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.rbh = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.sbh = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.gas = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.tv = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.bpr = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.sbpr = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.min && Object.hasOwnProperty.call(e, 'min') && o.uint32(8).int64(e.min),
            null != e.max && Object.hasOwnProperty.call(e, 'max') && o.uint32(16).int64(e.max),
            null != e.constant && Object.hasOwnProperty.call(e, 'constant') && o.uint32(24).int64(e.constant),
            null != e.bpt && Object.hasOwnProperty.call(e, 'bpt') && o.uint32(32).int64(e.bpt),
            null != e.vpt && Object.hasOwnProperty.call(e, 'vpt') && o.uint32(40).int64(e.vpt),
            null != e.rbh && Object.hasOwnProperty.call(e, 'rbh') && o.uint32(48).int64(e.rbh),
            null != e.sbh && Object.hasOwnProperty.call(e, 'sbh') && o.uint32(56).int64(e.sbh),
            null != e.gas && Object.hasOwnProperty.call(e, 'gas') && o.uint32(64).int64(e.gas),
            null != e.tv && Object.hasOwnProperty.call(e, 'tv') && o.uint32(72).int64(e.tv),
            null != e.bpr && Object.hasOwnProperty.call(e, 'bpr') && o.uint32(80).int64(e.bpr),
            null != e.sbpr && Object.hasOwnProperty.call(e, 'sbpr') && o.uint32(88).int64(e.sbpr),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FeeComponents(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.min = e.int64();
                break;
              case 2:
                i.max = e.int64();
                break;
              case 3:
                i.constant = e.int64();
                break;
              case 4:
                i.bpt = e.int64();
                break;
              case 5:
                i.vpt = e.int64();
                break;
              case 6:
                i.rbh = e.int64();
                break;
              case 7:
                i.sbh = e.int64();
                break;
              case 8:
                i.gas = e.int64();
                break;
              case 9:
                i.tv = e.int64();
                break;
              case 10:
                i.bpr = e.int64();
                break;
              case 11:
                i.sbpr = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionFeeSchedule: (function () {
      function e(e) {
        if (((this.fees = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.hederaFunctionality = 0),
        (e.prototype.feeData = null),
        (e.prototype.fees = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.hederaFunctionality &&
              Object.hasOwnProperty.call(e, 'hederaFunctionality') &&
              o.uint32(8).int32(e.hederaFunctionality),
            null != e.feeData &&
              Object.hasOwnProperty.call(e, 'feeData') &&
              $root.proto.FeeData.encode(e.feeData, o.uint32(18).fork()).ldelim(),
            null != e.fees && e.fees.length)
          )
            for (var t = 0; t < e.fees.length; ++t) $root.proto.FeeData.encode(e.fees[t], o.uint32(26).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionFeeSchedule(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.hederaFunctionality = e.int32();
                break;
              case 2:
                i.feeData = $root.proto.FeeData.decode(e, e.uint32());
                break;
              case 3:
                (i.fees && i.fees.length) || (i.fees = []), i.fees.push($root.proto.FeeData.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FeeData: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.nodedata = null),
        (e.prototype.networkdata = null),
        (e.prototype.servicedata = null),
        (e.prototype.subType = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.nodedata &&
              Object.hasOwnProperty.call(e, 'nodedata') &&
              $root.proto.FeeComponents.encode(e.nodedata, o.uint32(10).fork()).ldelim(),
            null != e.networkdata &&
              Object.hasOwnProperty.call(e, 'networkdata') &&
              $root.proto.FeeComponents.encode(e.networkdata, o.uint32(18).fork()).ldelim(),
            null != e.servicedata &&
              Object.hasOwnProperty.call(e, 'servicedata') &&
              $root.proto.FeeComponents.encode(e.servicedata, o.uint32(26).fork()).ldelim(),
            null != e.subType && Object.hasOwnProperty.call(e, 'subType') && o.uint32(32).int32(e.subType),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FeeData(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.nodedata = $root.proto.FeeComponents.decode(e, e.uint32());
                break;
              case 2:
                i.networkdata = $root.proto.FeeComponents.decode(e, e.uint32());
                break;
              case 3:
                i.servicedata = $root.proto.FeeComponents.decode(e, e.uint32());
                break;
              case 4:
                i.subType = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FeeSchedule: (function () {
      function e(e) {
        if (((this.transactionFeeSchedule = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.transactionFeeSchedule = $util.emptyArray),
        (e.prototype.expiryTime = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.transactionFeeSchedule && e.transactionFeeSchedule.length))
            for (var t = 0; t < e.transactionFeeSchedule.length; ++t)
              $root.proto.TransactionFeeSchedule.encode(e.transactionFeeSchedule[t], o.uint32(10).fork()).ldelim();
          return (
            null != e.expiryTime &&
              Object.hasOwnProperty.call(e, 'expiryTime') &&
              $root.proto.TimestampSeconds.encode(e.expiryTime, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FeeSchedule(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.transactionFeeSchedule && i.transactionFeeSchedule.length) || (i.transactionFeeSchedule = []),
                  i.transactionFeeSchedule.push($root.proto.TransactionFeeSchedule.decode(e, e.uint32()));
                break;
              case 2:
                i.expiryTime = $root.proto.TimestampSeconds.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CurrentAndNextFeeSchedule: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.currentFeeSchedule = null),
        (e.prototype.nextFeeSchedule = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.currentFeeSchedule &&
              Object.hasOwnProperty.call(e, 'currentFeeSchedule') &&
              $root.proto.FeeSchedule.encode(e.currentFeeSchedule, o.uint32(10).fork()).ldelim(),
            null != e.nextFeeSchedule &&
              Object.hasOwnProperty.call(e, 'nextFeeSchedule') &&
              $root.proto.FeeSchedule.encode(e.nextFeeSchedule, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CurrentAndNextFeeSchedule(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.currentFeeSchedule = $root.proto.FeeSchedule.decode(e, e.uint32());
                break;
              case 2:
                i.nextFeeSchedule = $root.proto.FeeSchedule.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ServiceEndpoint: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.ipAddressV4 = $util.newBuffer([])),
        (e.prototype.port = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.ipAddressV4 && Object.hasOwnProperty.call(e, 'ipAddressV4') && o.uint32(10).bytes(e.ipAddressV4),
            null != e.port && Object.hasOwnProperty.call(e, 'port') && o.uint32(16).int32(e.port),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ServiceEndpoint(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.ipAddressV4 = e.bytes();
                break;
              case 2:
                i.port = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NodeAddress: (function () {
      function e(e) {
        if (((this.serviceEndpoint = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.ipAddress = $util.newBuffer([])),
        (e.prototype.portno = 0),
        (e.prototype.memo = $util.newBuffer([])),
        (e.prototype.RSA_PubKey = ''),
        (e.prototype.nodeId = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.nodeAccountId = null),
        (e.prototype.nodeCertHash = $util.newBuffer([])),
        (e.prototype.serviceEndpoint = $util.emptyArray),
        (e.prototype.description = ''),
        (e.prototype.stake = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.ipAddress && Object.hasOwnProperty.call(e, 'ipAddress') && o.uint32(10).bytes(e.ipAddress),
            null != e.portno && Object.hasOwnProperty.call(e, 'portno') && o.uint32(16).int32(e.portno),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(26).bytes(e.memo),
            null != e.RSA_PubKey && Object.hasOwnProperty.call(e, 'RSA_PubKey') && o.uint32(34).string(e.RSA_PubKey),
            null != e.nodeId && Object.hasOwnProperty.call(e, 'nodeId') && o.uint32(40).int64(e.nodeId),
            null != e.nodeAccountId &&
              Object.hasOwnProperty.call(e, 'nodeAccountId') &&
              $root.proto.AccountID.encode(e.nodeAccountId, o.uint32(50).fork()).ldelim(),
            null != e.nodeCertHash &&
              Object.hasOwnProperty.call(e, 'nodeCertHash') &&
              o.uint32(58).bytes(e.nodeCertHash),
            null != e.serviceEndpoint && e.serviceEndpoint.length)
          )
            for (var t = 0; t < e.serviceEndpoint.length; ++t)
              $root.proto.ServiceEndpoint.encode(e.serviceEndpoint[t], o.uint32(66).fork()).ldelim();
          return (
            null != e.description && Object.hasOwnProperty.call(e, 'description') && o.uint32(74).string(e.description),
            null != e.stake && Object.hasOwnProperty.call(e, 'stake') && o.uint32(80).int64(e.stake),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NodeAddress(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.ipAddress = e.bytes();
                break;
              case 2:
                i.portno = e.int32();
                break;
              case 3:
                i.memo = e.bytes();
                break;
              case 4:
                i.RSA_PubKey = e.string();
                break;
              case 5:
                i.nodeId = e.int64();
                break;
              case 6:
                i.nodeAccountId = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 7:
                i.nodeCertHash = e.bytes();
                break;
              case 8:
                (i.serviceEndpoint && i.serviceEndpoint.length) || (i.serviceEndpoint = []),
                  i.serviceEndpoint.push($root.proto.ServiceEndpoint.decode(e, e.uint32()));
                break;
              case 9:
                i.description = e.string();
                break;
              case 10:
                i.stake = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NodeAddressBook: (function () {
      function e(e) {
        if (((this.nodeAddress = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.nodeAddress = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.nodeAddress && e.nodeAddress.length))
            for (var t = 0; t < e.nodeAddress.length; ++t)
              $root.proto.NodeAddress.encode(e.nodeAddress[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NodeAddressBook(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.nodeAddress && i.nodeAddress.length) || (i.nodeAddress = []),
                  i.nodeAddress.push($root.proto.NodeAddress.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SemanticVersion: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.major = 0),
        (e.prototype.minor = 0),
        (e.prototype.patch = 0),
        (e.prototype.pre = ''),
        (e.prototype.build = ''),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.major && Object.hasOwnProperty.call(e, 'major') && o.uint32(8).int32(e.major),
            null != e.minor && Object.hasOwnProperty.call(e, 'minor') && o.uint32(16).int32(e.minor),
            null != e.patch && Object.hasOwnProperty.call(e, 'patch') && o.uint32(24).int32(e.patch),
            null != e.pre && Object.hasOwnProperty.call(e, 'pre') && o.uint32(34).string(e.pre),
            null != e.build && Object.hasOwnProperty.call(e, 'build') && o.uint32(42).string(e.build),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SemanticVersion(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.major = e.int32();
                break;
              case 2:
                i.minor = e.int32();
                break;
              case 3:
                i.patch = e.int32();
                break;
              case 4:
                i.pre = e.string();
                break;
              case 5:
                i.build = e.string();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Setting: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.name = ''),
        (e.prototype.value = ''),
        (e.prototype.data = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.name && Object.hasOwnProperty.call(e, 'name') && o.uint32(10).string(e.name),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(18).string(e.value),
            null != e.data && Object.hasOwnProperty.call(e, 'data') && o.uint32(26).bytes(e.data),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Setting(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.name = e.string();
                break;
              case 2:
                i.value = e.string();
                break;
              case 3:
                i.data = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ServicesConfigurationList: (function () {
      function e(e) {
        if (((this.nameValue = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.nameValue = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.nameValue && e.nameValue.length))
            for (var t = 0; t < e.nameValue.length; ++t)
              $root.proto.Setting.encode(e.nameValue[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ServicesConfigurationList(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.nameValue && i.nameValue.length) || (i.nameValue = []),
                  i.nameValue.push($root.proto.Setting.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenRelationship: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenId = null),
        (e.prototype.symbol = ''),
        (e.prototype.balance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.kycStatus = 0),
        (e.prototype.freezeStatus = 0),
        (e.prototype.decimals = 0),
        (e.prototype.automaticAssociation = !1),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.tokenId &&
              Object.hasOwnProperty.call(e, 'tokenId') &&
              $root.proto.TokenID.encode(e.tokenId, o.uint32(10).fork()).ldelim(),
            null != e.symbol && Object.hasOwnProperty.call(e, 'symbol') && o.uint32(18).string(e.symbol),
            null != e.balance && Object.hasOwnProperty.call(e, 'balance') && o.uint32(24).uint64(e.balance),
            null != e.kycStatus && Object.hasOwnProperty.call(e, 'kycStatus') && o.uint32(32).int32(e.kycStatus),
            null != e.freezeStatus &&
              Object.hasOwnProperty.call(e, 'freezeStatus') &&
              o.uint32(40).int32(e.freezeStatus),
            null != e.decimals && Object.hasOwnProperty.call(e, 'decimals') && o.uint32(48).uint32(e.decimals),
            null != e.automaticAssociation &&
              Object.hasOwnProperty.call(e, 'automaticAssociation') &&
              o.uint32(56).bool(e.automaticAssociation),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenRelationship(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.tokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.symbol = e.string();
                break;
              case 3:
                i.balance = e.uint64();
                break;
              case 4:
                i.kycStatus = e.int32();
                break;
              case 5:
                i.freezeStatus = e.int32();
                break;
              case 6:
                i.decimals = e.uint32();
                break;
              case 7:
                i.automaticAssociation = e.bool();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenBalance: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenId = null),
        (e.prototype.balance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.decimals = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.tokenId &&
              Object.hasOwnProperty.call(e, 'tokenId') &&
              $root.proto.TokenID.encode(e.tokenId, o.uint32(10).fork()).ldelim(),
            null != e.balance && Object.hasOwnProperty.call(e, 'balance') && o.uint32(16).uint64(e.balance),
            null != e.decimals && Object.hasOwnProperty.call(e, 'decimals') && o.uint32(24).uint32(e.decimals),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenBalance(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.tokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.balance = e.uint64();
                break;
              case 3:
                i.decimals = e.uint32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenBalances: (function () {
      function e(e) {
        if (((this.tokenBalances = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenBalances = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.tokenBalances && e.tokenBalances.length))
            for (var t = 0; t < e.tokenBalances.length; ++t)
              $root.proto.TokenBalance.encode(e.tokenBalances[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenBalances(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.tokenBalances && i.tokenBalances.length) || (i.tokenBalances = []),
                  i.tokenBalances.push($root.proto.TokenBalance.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenAssociation: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenId = null),
        (e.prototype.accountId = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.tokenId &&
              Object.hasOwnProperty.call(e, 'tokenId') &&
              $root.proto.TokenID.encode(e.tokenId, o.uint32(10).fork()).ldelim(),
            null != e.accountId &&
              Object.hasOwnProperty.call(e, 'accountId') &&
              $root.proto.AccountID.encode(e.accountId, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenAssociation(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.tokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.accountId = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Timestamp: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.seconds = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.nanos = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.seconds && Object.hasOwnProperty.call(e, 'seconds') && o.uint32(8).int64(e.seconds),
            null != e.nanos && Object.hasOwnProperty.call(e, 'nanos') && o.uint32(16).int32(e.nanos),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Timestamp(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.seconds = e.int64();
                break;
              case 2:
                i.nanos = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TimestampSeconds: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.seconds = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.seconds && Object.hasOwnProperty.call(e, 'seconds') && o.uint32(8).int64(e.seconds),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TimestampSeconds(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.seconds = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusCreateTopicTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.memo = ''),
        (e.prototype.adminKey = null),
        (e.prototype.submitKey = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.autoRenewAccount = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(10).string(e.memo),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(18).fork()).ldelim(),
            null != e.submitKey &&
              Object.hasOwnProperty.call(e, 'submitKey') &&
              $root.proto.Key.encode(e.submitKey, o.uint32(26).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(50).fork()).ldelim(),
            null != e.autoRenewAccount &&
              Object.hasOwnProperty.call(e, 'autoRenewAccount') &&
              $root.proto.AccountID.encode(e.autoRenewAccount, o.uint32(58).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusCreateTopicTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.memo = e.string();
                break;
              case 2:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 3:
                i.submitKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 6:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 7:
                i.autoRenewAccount = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Duration: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.seconds = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.seconds && Object.hasOwnProperty.call(e, 'seconds') && o.uint32(8).int64(e.seconds),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Duration(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.seconds = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusDeleteTopicTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.topicID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.topicID &&
              Object.hasOwnProperty.call(e, 'topicID') &&
              $root.proto.TopicID.encode(e.topicID, o.uint32(10).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusDeleteTopicTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.topicID = $root.proto.TopicID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusGetTopicInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.topicID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.topicID &&
              Object.hasOwnProperty.call(e, 'topicID') &&
              $root.proto.TopicID.encode(e.topicID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusGetTopicInfoQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.topicID = $root.proto.TopicID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusGetTopicInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.topicID = null),
        (e.prototype.topicInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.topicID &&
              Object.hasOwnProperty.call(e, 'topicID') &&
              $root.proto.TopicID.encode(e.topicID, o.uint32(18).fork()).ldelim(),
            null != e.topicInfo &&
              Object.hasOwnProperty.call(e, 'topicInfo') &&
              $root.proto.ConsensusTopicInfo.encode(e.topicInfo, o.uint32(42).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusGetTopicInfoResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.topicID = $root.proto.TopicID.decode(e, e.uint32());
                break;
              case 5:
                i.topicInfo = $root.proto.ConsensusTopicInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ResponseType: (function () {
      const e = {},
        o = Object.create(e);
      return (
        (o[(e[0] = 'ANSWER_ONLY')] = 0),
        (o[(e[1] = 'ANSWER_STATE_PROOF')] = 1),
        (o[(e[2] = 'COST_ANSWER')] = 2),
        (o[(e[3] = 'COST_ANSWER_STATE_PROOF')] = 3),
        o
      );
    })(),
    QueryHeader: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.payment = null),
        (e.prototype.responseType = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.payment &&
              Object.hasOwnProperty.call(e, 'payment') &&
              $root.proto.Transaction.encode(e.payment, o.uint32(10).fork()).ldelim(),
            null != e.responseType &&
              Object.hasOwnProperty.call(e, 'responseType') &&
              o.uint32(16).int32(e.responseType),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.QueryHeader(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.payment = $root.proto.Transaction.decode(e, e.uint32());
                break;
              case 2:
                i.responseType = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Transaction: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.body = null),
        (e.prototype.sigs = null),
        (e.prototype.sigMap = null),
        (e.prototype.bodyBytes = $util.newBuffer([])),
        (e.prototype.signedTransactionBytes = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.body &&
              Object.hasOwnProperty.call(e, 'body') &&
              $root.proto.TransactionBody.encode(e.body, o.uint32(10).fork()).ldelim(),
            null != e.sigs &&
              Object.hasOwnProperty.call(e, 'sigs') &&
              $root.proto.SignatureList.encode(e.sigs, o.uint32(18).fork()).ldelim(),
            null != e.sigMap &&
              Object.hasOwnProperty.call(e, 'sigMap') &&
              $root.proto.SignatureMap.encode(e.sigMap, o.uint32(26).fork()).ldelim(),
            null != e.bodyBytes && Object.hasOwnProperty.call(e, 'bodyBytes') && o.uint32(34).bytes(e.bodyBytes),
            null != e.signedTransactionBytes &&
              Object.hasOwnProperty.call(e, 'signedTransactionBytes') &&
              o.uint32(42).bytes(e.signedTransactionBytes),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Transaction(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.body = $root.proto.TransactionBody.decode(e, e.uint32());
                break;
              case 2:
                i.sigs = $root.proto.SignatureList.decode(e, e.uint32());
                break;
              case 3:
                i.sigMap = $root.proto.SignatureMap.decode(e, e.uint32());
                break;
              case 4:
                i.bodyBytes = e.bytes();
                break;
              case 5:
                i.signedTransactionBytes = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.transactionID = null),
        (e.prototype.nodeAccountID = null),
        (e.prototype.transactionFee = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.transactionValidDuration = null),
        (e.prototype.generateRecord = !1),
        (e.prototype.memo = ''),
        (e.prototype.contractCall = null),
        (e.prototype.contractCreateInstance = null),
        (e.prototype.contractUpdateInstance = null),
        (e.prototype.contractDeleteInstance = null),
        (e.prototype.cryptoAddLiveHash = null),
        (e.prototype.cryptoCreateAccount = null),
        (e.prototype.cryptoDelete = null),
        (e.prototype.cryptoDeleteLiveHash = null),
        (e.prototype.cryptoTransfer = null),
        (e.prototype.cryptoUpdateAccount = null),
        (e.prototype.fileAppend = null),
        (e.prototype.fileCreate = null),
        (e.prototype.fileDelete = null),
        (e.prototype.fileUpdate = null),
        (e.prototype.systemDelete = null),
        (e.prototype.systemUndelete = null),
        (e.prototype.freeze = null),
        (e.prototype.consensusCreateTopic = null),
        (e.prototype.consensusUpdateTopic = null),
        (e.prototype.consensusDeleteTopic = null),
        (e.prototype.consensusSubmitMessage = null),
        (e.prototype.uncheckedSubmit = null),
        (e.prototype.tokenCreation = null),
        (e.prototype.tokenFreeze = null),
        (e.prototype.tokenUnfreeze = null),
        (e.prototype.tokenGrantKyc = null),
        (e.prototype.tokenRevokeKyc = null),
        (e.prototype.tokenDeletion = null),
        (e.prototype.tokenUpdate = null),
        (e.prototype.tokenMint = null),
        (e.prototype.tokenBurn = null),
        (e.prototype.tokenWipe = null),
        (e.prototype.tokenAssociate = null),
        (e.prototype.tokenDissociate = null),
        (e.prototype.tokenFeeScheduleUpdate = null),
        (e.prototype.tokenPause = null),
        (e.prototype.tokenUnpause = null),
        (e.prototype.scheduleCreate = null),
        (e.prototype.scheduleDelete = null),
        (e.prototype.scheduleSign = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'data', {
          get: $util.oneOfGetter(
            (o = [
              'contractCall',
              'contractCreateInstance',
              'contractUpdateInstance',
              'contractDeleteInstance',
              'cryptoAddLiveHash',
              'cryptoCreateAccount',
              'cryptoDelete',
              'cryptoDeleteLiveHash',
              'cryptoTransfer',
              'cryptoUpdateAccount',
              'fileAppend',
              'fileCreate',
              'fileDelete',
              'fileUpdate',
              'systemDelete',
              'systemUndelete',
              'freeze',
              'consensusCreateTopic',
              'consensusUpdateTopic',
              'consensusDeleteTopic',
              'consensusSubmitMessage',
              'uncheckedSubmit',
              'tokenCreation',
              'tokenFreeze',
              'tokenUnfreeze',
              'tokenGrantKyc',
              'tokenRevokeKyc',
              'tokenDeletion',
              'tokenUpdate',
              'tokenMint',
              'tokenBurn',
              'tokenWipe',
              'tokenAssociate',
              'tokenDissociate',
              'tokenFeeScheduleUpdate',
              'tokenPause',
              'tokenUnpause',
              'scheduleCreate',
              'scheduleDelete',
              'scheduleSign',
            ])
          ),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.transactionID &&
              Object.hasOwnProperty.call(e, 'transactionID') &&
              $root.proto.TransactionID.encode(e.transactionID, o.uint32(10).fork()).ldelim(),
            null != e.nodeAccountID &&
              Object.hasOwnProperty.call(e, 'nodeAccountID') &&
              $root.proto.AccountID.encode(e.nodeAccountID, o.uint32(18).fork()).ldelim(),
            null != e.transactionFee &&
              Object.hasOwnProperty.call(e, 'transactionFee') &&
              o.uint32(24).uint64(e.transactionFee),
            null != e.transactionValidDuration &&
              Object.hasOwnProperty.call(e, 'transactionValidDuration') &&
              $root.proto.Duration.encode(e.transactionValidDuration, o.uint32(34).fork()).ldelim(),
            null != e.generateRecord &&
              Object.hasOwnProperty.call(e, 'generateRecord') &&
              o.uint32(40).bool(e.generateRecord),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(50).string(e.memo),
            null != e.contractCall &&
              Object.hasOwnProperty.call(e, 'contractCall') &&
              $root.proto.ContractCallTransactionBody.encode(e.contractCall, o.uint32(58).fork()).ldelim(),
            null != e.contractCreateInstance &&
              Object.hasOwnProperty.call(e, 'contractCreateInstance') &&
              $root.proto.ContractCreateTransactionBody.encode(e.contractCreateInstance, o.uint32(66).fork()).ldelim(),
            null != e.contractUpdateInstance &&
              Object.hasOwnProperty.call(e, 'contractUpdateInstance') &&
              $root.proto.ContractUpdateTransactionBody.encode(e.contractUpdateInstance, o.uint32(74).fork()).ldelim(),
            null != e.cryptoAddLiveHash &&
              Object.hasOwnProperty.call(e, 'cryptoAddLiveHash') &&
              $root.proto.CryptoAddLiveHashTransactionBody.encode(e.cryptoAddLiveHash, o.uint32(82).fork()).ldelim(),
            null != e.cryptoCreateAccount &&
              Object.hasOwnProperty.call(e, 'cryptoCreateAccount') &&
              $root.proto.CryptoCreateTransactionBody.encode(e.cryptoCreateAccount, o.uint32(90).fork()).ldelim(),
            null != e.cryptoDelete &&
              Object.hasOwnProperty.call(e, 'cryptoDelete') &&
              $root.proto.CryptoDeleteTransactionBody.encode(e.cryptoDelete, o.uint32(98).fork()).ldelim(),
            null != e.cryptoDeleteLiveHash &&
              Object.hasOwnProperty.call(e, 'cryptoDeleteLiveHash') &&
              $root.proto.CryptoDeleteLiveHashTransactionBody.encode(
                e.cryptoDeleteLiveHash,
                o.uint32(106).fork()
              ).ldelim(),
            null != e.cryptoTransfer &&
              Object.hasOwnProperty.call(e, 'cryptoTransfer') &&
              $root.proto.CryptoTransferTransactionBody.encode(e.cryptoTransfer, o.uint32(114).fork()).ldelim(),
            null != e.cryptoUpdateAccount &&
              Object.hasOwnProperty.call(e, 'cryptoUpdateAccount') &&
              $root.proto.CryptoUpdateTransactionBody.encode(e.cryptoUpdateAccount, o.uint32(122).fork()).ldelim(),
            null != e.fileAppend &&
              Object.hasOwnProperty.call(e, 'fileAppend') &&
              $root.proto.FileAppendTransactionBody.encode(e.fileAppend, o.uint32(130).fork()).ldelim(),
            null != e.fileCreate &&
              Object.hasOwnProperty.call(e, 'fileCreate') &&
              $root.proto.FileCreateTransactionBody.encode(e.fileCreate, o.uint32(138).fork()).ldelim(),
            null != e.fileDelete &&
              Object.hasOwnProperty.call(e, 'fileDelete') &&
              $root.proto.FileDeleteTransactionBody.encode(e.fileDelete, o.uint32(146).fork()).ldelim(),
            null != e.fileUpdate &&
              Object.hasOwnProperty.call(e, 'fileUpdate') &&
              $root.proto.FileUpdateTransactionBody.encode(e.fileUpdate, o.uint32(154).fork()).ldelim(),
            null != e.systemDelete &&
              Object.hasOwnProperty.call(e, 'systemDelete') &&
              $root.proto.SystemDeleteTransactionBody.encode(e.systemDelete, o.uint32(162).fork()).ldelim(),
            null != e.systemUndelete &&
              Object.hasOwnProperty.call(e, 'systemUndelete') &&
              $root.proto.SystemUndeleteTransactionBody.encode(e.systemUndelete, o.uint32(170).fork()).ldelim(),
            null != e.contractDeleteInstance &&
              Object.hasOwnProperty.call(e, 'contractDeleteInstance') &&
              $root.proto.ContractDeleteTransactionBody.encode(e.contractDeleteInstance, o.uint32(178).fork()).ldelim(),
            null != e.freeze &&
              Object.hasOwnProperty.call(e, 'freeze') &&
              $root.proto.FreezeTransactionBody.encode(e.freeze, o.uint32(186).fork()).ldelim(),
            null != e.consensusCreateTopic &&
              Object.hasOwnProperty.call(e, 'consensusCreateTopic') &&
              $root.proto.ConsensusCreateTopicTransactionBody.encode(
                e.consensusCreateTopic,
                o.uint32(194).fork()
              ).ldelim(),
            null != e.consensusUpdateTopic &&
              Object.hasOwnProperty.call(e, 'consensusUpdateTopic') &&
              $root.proto.ConsensusUpdateTopicTransactionBody.encode(
                e.consensusUpdateTopic,
                o.uint32(202).fork()
              ).ldelim(),
            null != e.consensusDeleteTopic &&
              Object.hasOwnProperty.call(e, 'consensusDeleteTopic') &&
              $root.proto.ConsensusDeleteTopicTransactionBody.encode(
                e.consensusDeleteTopic,
                o.uint32(210).fork()
              ).ldelim(),
            null != e.consensusSubmitMessage &&
              Object.hasOwnProperty.call(e, 'consensusSubmitMessage') &&
              $root.proto.ConsensusSubmitMessageTransactionBody.encode(
                e.consensusSubmitMessage,
                o.uint32(218).fork()
              ).ldelim(),
            null != e.uncheckedSubmit &&
              Object.hasOwnProperty.call(e, 'uncheckedSubmit') &&
              $root.proto.UncheckedSubmitBody.encode(e.uncheckedSubmit, o.uint32(226).fork()).ldelim(),
            null != e.tokenCreation &&
              Object.hasOwnProperty.call(e, 'tokenCreation') &&
              $root.proto.TokenCreateTransactionBody.encode(e.tokenCreation, o.uint32(234).fork()).ldelim(),
            null != e.tokenFreeze &&
              Object.hasOwnProperty.call(e, 'tokenFreeze') &&
              $root.proto.TokenFreezeAccountTransactionBody.encode(e.tokenFreeze, o.uint32(250).fork()).ldelim(),
            null != e.tokenUnfreeze &&
              Object.hasOwnProperty.call(e, 'tokenUnfreeze') &&
              $root.proto.TokenUnfreezeAccountTransactionBody.encode(e.tokenUnfreeze, o.uint32(258).fork()).ldelim(),
            null != e.tokenGrantKyc &&
              Object.hasOwnProperty.call(e, 'tokenGrantKyc') &&
              $root.proto.TokenGrantKycTransactionBody.encode(e.tokenGrantKyc, o.uint32(266).fork()).ldelim(),
            null != e.tokenRevokeKyc &&
              Object.hasOwnProperty.call(e, 'tokenRevokeKyc') &&
              $root.proto.TokenRevokeKycTransactionBody.encode(e.tokenRevokeKyc, o.uint32(274).fork()).ldelim(),
            null != e.tokenDeletion &&
              Object.hasOwnProperty.call(e, 'tokenDeletion') &&
              $root.proto.TokenDeleteTransactionBody.encode(e.tokenDeletion, o.uint32(282).fork()).ldelim(),
            null != e.tokenUpdate &&
              Object.hasOwnProperty.call(e, 'tokenUpdate') &&
              $root.proto.TokenUpdateTransactionBody.encode(e.tokenUpdate, o.uint32(290).fork()).ldelim(),
            null != e.tokenMint &&
              Object.hasOwnProperty.call(e, 'tokenMint') &&
              $root.proto.TokenMintTransactionBody.encode(e.tokenMint, o.uint32(298).fork()).ldelim(),
            null != e.tokenBurn &&
              Object.hasOwnProperty.call(e, 'tokenBurn') &&
              $root.proto.TokenBurnTransactionBody.encode(e.tokenBurn, o.uint32(306).fork()).ldelim(),
            null != e.tokenWipe &&
              Object.hasOwnProperty.call(e, 'tokenWipe') &&
              $root.proto.TokenWipeAccountTransactionBody.encode(e.tokenWipe, o.uint32(314).fork()).ldelim(),
            null != e.tokenAssociate &&
              Object.hasOwnProperty.call(e, 'tokenAssociate') &&
              $root.proto.TokenAssociateTransactionBody.encode(e.tokenAssociate, o.uint32(322).fork()).ldelim(),
            null != e.tokenDissociate &&
              Object.hasOwnProperty.call(e, 'tokenDissociate') &&
              $root.proto.TokenDissociateTransactionBody.encode(e.tokenDissociate, o.uint32(330).fork()).ldelim(),
            null != e.scheduleCreate &&
              Object.hasOwnProperty.call(e, 'scheduleCreate') &&
              $root.proto.ScheduleCreateTransactionBody.encode(e.scheduleCreate, o.uint32(338).fork()).ldelim(),
            null != e.scheduleDelete &&
              Object.hasOwnProperty.call(e, 'scheduleDelete') &&
              $root.proto.ScheduleDeleteTransactionBody.encode(e.scheduleDelete, o.uint32(346).fork()).ldelim(),
            null != e.scheduleSign &&
              Object.hasOwnProperty.call(e, 'scheduleSign') &&
              $root.proto.ScheduleSignTransactionBody.encode(e.scheduleSign, o.uint32(354).fork()).ldelim(),
            null != e.tokenFeeScheduleUpdate &&
              Object.hasOwnProperty.call(e, 'tokenFeeScheduleUpdate') &&
              $root.proto.TokenFeeScheduleUpdateTransactionBody.encode(
                e.tokenFeeScheduleUpdate,
                o.uint32(362).fork()
              ).ldelim(),
            null != e.tokenPause &&
              Object.hasOwnProperty.call(e, 'tokenPause') &&
              $root.proto.TokenPauseTransactionBody.encode(e.tokenPause, o.uint32(370).fork()).ldelim(),
            null != e.tokenUnpause &&
              Object.hasOwnProperty.call(e, 'tokenUnpause') &&
              $root.proto.TokenUnpauseTransactionBody.encode(e.tokenUnpause, o.uint32(378).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionBody(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.transactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              case 2:
                i.nodeAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.transactionFee = e.uint64();
                break;
              case 4:
                i.transactionValidDuration = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 5:
                i.generateRecord = e.bool();
                break;
              case 6:
                i.memo = e.string();
                break;
              case 7:
                i.contractCall = $root.proto.ContractCallTransactionBody.decode(e, e.uint32());
                break;
              case 8:
                i.contractCreateInstance = $root.proto.ContractCreateTransactionBody.decode(e, e.uint32());
                break;
              case 9:
                i.contractUpdateInstance = $root.proto.ContractUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 22:
                i.contractDeleteInstance = $root.proto.ContractDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 10:
                i.cryptoAddLiveHash = $root.proto.CryptoAddLiveHashTransactionBody.decode(e, e.uint32());
                break;
              case 11:
                i.cryptoCreateAccount = $root.proto.CryptoCreateTransactionBody.decode(e, e.uint32());
                break;
              case 12:
                i.cryptoDelete = $root.proto.CryptoDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 13:
                i.cryptoDeleteLiveHash = $root.proto.CryptoDeleteLiveHashTransactionBody.decode(e, e.uint32());
                break;
              case 14:
                i.cryptoTransfer = $root.proto.CryptoTransferTransactionBody.decode(e, e.uint32());
                break;
              case 15:
                i.cryptoUpdateAccount = $root.proto.CryptoUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 16:
                i.fileAppend = $root.proto.FileAppendTransactionBody.decode(e, e.uint32());
                break;
              case 17:
                i.fileCreate = $root.proto.FileCreateTransactionBody.decode(e, e.uint32());
                break;
              case 18:
                i.fileDelete = $root.proto.FileDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 19:
                i.fileUpdate = $root.proto.FileUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 20:
                i.systemDelete = $root.proto.SystemDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 21:
                i.systemUndelete = $root.proto.SystemUndeleteTransactionBody.decode(e, e.uint32());
                break;
              case 23:
                i.freeze = $root.proto.FreezeTransactionBody.decode(e, e.uint32());
                break;
              case 24:
                i.consensusCreateTopic = $root.proto.ConsensusCreateTopicTransactionBody.decode(e, e.uint32());
                break;
              case 25:
                i.consensusUpdateTopic = $root.proto.ConsensusUpdateTopicTransactionBody.decode(e, e.uint32());
                break;
              case 26:
                i.consensusDeleteTopic = $root.proto.ConsensusDeleteTopicTransactionBody.decode(e, e.uint32());
                break;
              case 27:
                i.consensusSubmitMessage = $root.proto.ConsensusSubmitMessageTransactionBody.decode(e, e.uint32());
                break;
              case 28:
                i.uncheckedSubmit = $root.proto.UncheckedSubmitBody.decode(e, e.uint32());
                break;
              case 29:
                i.tokenCreation = $root.proto.TokenCreateTransactionBody.decode(e, e.uint32());
                break;
              case 31:
                i.tokenFreeze = $root.proto.TokenFreezeAccountTransactionBody.decode(e, e.uint32());
                break;
              case 32:
                i.tokenUnfreeze = $root.proto.TokenUnfreezeAccountTransactionBody.decode(e, e.uint32());
                break;
              case 33:
                i.tokenGrantKyc = $root.proto.TokenGrantKycTransactionBody.decode(e, e.uint32());
                break;
              case 34:
                i.tokenRevokeKyc = $root.proto.TokenRevokeKycTransactionBody.decode(e, e.uint32());
                break;
              case 35:
                i.tokenDeletion = $root.proto.TokenDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 36:
                i.tokenUpdate = $root.proto.TokenUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 37:
                i.tokenMint = $root.proto.TokenMintTransactionBody.decode(e, e.uint32());
                break;
              case 38:
                i.tokenBurn = $root.proto.TokenBurnTransactionBody.decode(e, e.uint32());
                break;
              case 39:
                i.tokenWipe = $root.proto.TokenWipeAccountTransactionBody.decode(e, e.uint32());
                break;
              case 40:
                i.tokenAssociate = $root.proto.TokenAssociateTransactionBody.decode(e, e.uint32());
                break;
              case 41:
                i.tokenDissociate = $root.proto.TokenDissociateTransactionBody.decode(e, e.uint32());
                break;
              case 45:
                i.tokenFeeScheduleUpdate = $root.proto.TokenFeeScheduleUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 46:
                i.tokenPause = $root.proto.TokenPauseTransactionBody.decode(e, e.uint32());
                break;
              case 47:
                i.tokenUnpause = $root.proto.TokenUnpauseTransactionBody.decode(e, e.uint32());
                break;
              case 42:
                i.scheduleCreate = $root.proto.ScheduleCreateTransactionBody.decode(e, e.uint32());
                break;
              case 43:
                i.scheduleDelete = $root.proto.ScheduleDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 44:
                i.scheduleSign = $root.proto.ScheduleSignTransactionBody.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SystemDeleteTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.fileID = null), (e.prototype.contractID = null), (e.prototype.expirationTime = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'id', {
          get: $util.oneOfGetter((o = ['fileID', 'contractID'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(10).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(18).fork()).ldelim(),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.TimestampSeconds.encode(e.expirationTime, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SystemDeleteTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 2:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 3:
                i.expirationTime = $root.proto.TimestampSeconds.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SystemUndeleteTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.fileID = null), (e.prototype.contractID = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'id', {
          get: $util.oneOfGetter((o = ['fileID', 'contractID'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(10).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SystemUndeleteTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 2:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FreezeTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.startHour = 0),
        (e.prototype.startMin = 0),
        (e.prototype.endHour = 0),
        (e.prototype.endMin = 0),
        (e.prototype.updateFile = null),
        (e.prototype.fileHash = $util.newBuffer([])),
        (e.prototype.startTime = null),
        (e.prototype.freezeType = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.startHour && Object.hasOwnProperty.call(e, 'startHour') && o.uint32(8).int32(e.startHour),
            null != e.startMin && Object.hasOwnProperty.call(e, 'startMin') && o.uint32(16).int32(e.startMin),
            null != e.endHour && Object.hasOwnProperty.call(e, 'endHour') && o.uint32(24).int32(e.endHour),
            null != e.endMin && Object.hasOwnProperty.call(e, 'endMin') && o.uint32(32).int32(e.endMin),
            null != e.updateFile &&
              Object.hasOwnProperty.call(e, 'updateFile') &&
              $root.proto.FileID.encode(e.updateFile, o.uint32(42).fork()).ldelim(),
            null != e.fileHash && Object.hasOwnProperty.call(e, 'fileHash') && o.uint32(50).bytes(e.fileHash),
            null != e.startTime &&
              Object.hasOwnProperty.call(e, 'startTime') &&
              $root.proto.Timestamp.encode(e.startTime, o.uint32(58).fork()).ldelim(),
            null != e.freezeType && Object.hasOwnProperty.call(e, 'freezeType') && o.uint32(64).int32(e.freezeType),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FreezeTransactionBody(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.startHour = e.int32();
                break;
              case 2:
                i.startMin = e.int32();
                break;
              case 3:
                i.endHour = e.int32();
                break;
              case 4:
                i.endMin = e.int32();
                break;
              case 5:
                i.updateFile = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 6:
                i.fileHash = e.bytes();
                break;
              case 7:
                i.startTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 8:
                i.freezeType = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FreezeType: (function () {
      const e = {},
        o = Object.create(e);
      return (
        (o[(e[0] = 'UNKNOWN_FREEZE_TYPE')] = 0),
        (o[(e[1] = 'FREEZE_ONLY')] = 1),
        (o[(e[2] = 'PREPARE_UPGRADE')] = 2),
        (o[(e[3] = 'FREEZE_UPGRADE')] = 3),
        (o[(e[4] = 'FREEZE_ABORT')] = 4),
        (o[(e[5] = 'TELEMETRY_UPGRADE')] = 5),
        o
      );
    })(),
    ContractCallTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.contractID = null),
        (e.prototype.gas = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.functionParameters = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(10).fork()).ldelim(),
            null != e.gas && Object.hasOwnProperty.call(e, 'gas') && o.uint32(16).int64(e.gas),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(24).int64(e.amount),
            null != e.functionParameters &&
              Object.hasOwnProperty.call(e, 'functionParameters') &&
              o.uint32(34).bytes(e.functionParameters),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractCallTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 2:
                i.gas = e.int64();
                break;
              case 3:
                i.amount = e.int64();
                break;
              case 4:
                i.functionParameters = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractCreateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.fileID = null),
        (e.prototype.adminKey = null),
        (e.prototype.gas = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.initialBalance = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.proxyAccountID = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.constructorParameters = $util.newBuffer([])),
        (e.prototype.shardID = null),
        (e.prototype.realmID = null),
        (e.prototype.newRealmAdminKey = null),
        (e.prototype.memo = ''),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(10).fork()).ldelim(),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(26).fork()).ldelim(),
            null != e.gas && Object.hasOwnProperty.call(e, 'gas') && o.uint32(32).int64(e.gas),
            null != e.initialBalance &&
              Object.hasOwnProperty.call(e, 'initialBalance') &&
              o.uint32(40).int64(e.initialBalance),
            null != e.proxyAccountID &&
              Object.hasOwnProperty.call(e, 'proxyAccountID') &&
              $root.proto.AccountID.encode(e.proxyAccountID, o.uint32(50).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(66).fork()).ldelim(),
            null != e.constructorParameters &&
              Object.hasOwnProperty.call(e, 'constructorParameters') &&
              o.uint32(74).bytes(e.constructorParameters),
            null != e.shardID &&
              Object.hasOwnProperty.call(e, 'shardID') &&
              $root.proto.ShardID.encode(e.shardID, o.uint32(82).fork()).ldelim(),
            null != e.realmID &&
              Object.hasOwnProperty.call(e, 'realmID') &&
              $root.proto.RealmID.encode(e.realmID, o.uint32(90).fork()).ldelim(),
            null != e.newRealmAdminKey &&
              Object.hasOwnProperty.call(e, 'newRealmAdminKey') &&
              $root.proto.Key.encode(e.newRealmAdminKey, o.uint32(98).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(106).string(e.memo),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractCreateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 3:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 4:
                i.gas = e.int64();
                break;
              case 5:
                i.initialBalance = e.int64();
                break;
              case 6:
                i.proxyAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 8:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 9:
                i.constructorParameters = e.bytes();
                break;
              case 10:
                i.shardID = $root.proto.ShardID.decode(e, e.uint32());
                break;
              case 11:
                i.realmID = $root.proto.RealmID.decode(e, e.uint32());
                break;
              case 12:
                i.newRealmAdminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 13:
                i.memo = e.string();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractUpdateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.contractID = null),
        (e.prototype.expirationTime = null),
        (e.prototype.adminKey = null),
        (e.prototype.proxyAccountID = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.fileID = null),
        (e.prototype.memo = null),
        (e.prototype.memoWrapper = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'memoField', {
          get: $util.oneOfGetter((o = ['memo', 'memoWrapper'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(10).fork()).ldelim(),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.Timestamp.encode(e.expirationTime, o.uint32(18).fork()).ldelim(),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(26).fork()).ldelim(),
            null != e.proxyAccountID &&
              Object.hasOwnProperty.call(e, 'proxyAccountID') &&
              $root.proto.AccountID.encode(e.proxyAccountID, o.uint32(50).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(58).fork()).ldelim(),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(66).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(74).string(e.memo),
            null != e.memoWrapper &&
              Object.hasOwnProperty.call(e, 'memoWrapper') &&
              $root.google.protobuf.StringValue.encode(e.memoWrapper, o.uint32(82).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractUpdateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 2:
                i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 3:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 6:
                i.proxyAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 7:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 8:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 9:
                i.memo = e.string();
                break;
              case 10:
                i.memoWrapper = $root.google.protobuf.StringValue.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    LiveHash: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.accountId = null),
        (e.prototype.hash = $util.newBuffer([])),
        (e.prototype.keys = null),
        (e.prototype.duration = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.accountId &&
              Object.hasOwnProperty.call(e, 'accountId') &&
              $root.proto.AccountID.encode(e.accountId, o.uint32(10).fork()).ldelim(),
            null != e.hash && Object.hasOwnProperty.call(e, 'hash') && o.uint32(18).bytes(e.hash),
            null != e.keys &&
              Object.hasOwnProperty.call(e, 'keys') &&
              $root.proto.KeyList.encode(e.keys, o.uint32(26).fork()).ldelim(),
            null != e.duration &&
              Object.hasOwnProperty.call(e, 'duration') &&
              $root.proto.Duration.encode(e.duration, o.uint32(42).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.LiveHash(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.accountId = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.hash = e.bytes();
                break;
              case 3:
                i.keys = $root.proto.KeyList.decode(e, e.uint32());
                break;
              case 5:
                i.duration = $root.proto.Duration.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoAddLiveHashTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.liveHash = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.liveHash &&
              Object.hasOwnProperty.call(e, 'liveHash') &&
              $root.proto.LiveHash.encode(e.liveHash, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoAddLiveHashTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 3:
                i.liveHash = $root.proto.LiveHash.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoCreateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.key = null),
        (e.prototype.initialBalance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.proxyAccountID = null),
        (e.prototype.sendRecordThreshold = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.receiveRecordThreshold = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.receiverSigRequired = !1),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.shardID = null),
        (e.prototype.realmID = null),
        (e.prototype.newRealmAdminKey = null),
        (e.prototype.memo = ''),
        (e.prototype.maxAutomaticTokenAssociations = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.key &&
              Object.hasOwnProperty.call(e, 'key') &&
              $root.proto.Key.encode(e.key, o.uint32(10).fork()).ldelim(),
            null != e.initialBalance &&
              Object.hasOwnProperty.call(e, 'initialBalance') &&
              o.uint32(16).uint64(e.initialBalance),
            null != e.proxyAccountID &&
              Object.hasOwnProperty.call(e, 'proxyAccountID') &&
              $root.proto.AccountID.encode(e.proxyAccountID, o.uint32(26).fork()).ldelim(),
            null != e.sendRecordThreshold &&
              Object.hasOwnProperty.call(e, 'sendRecordThreshold') &&
              o.uint32(48).uint64(e.sendRecordThreshold),
            null != e.receiveRecordThreshold &&
              Object.hasOwnProperty.call(e, 'receiveRecordThreshold') &&
              o.uint32(56).uint64(e.receiveRecordThreshold),
            null != e.receiverSigRequired &&
              Object.hasOwnProperty.call(e, 'receiverSigRequired') &&
              o.uint32(64).bool(e.receiverSigRequired),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(74).fork()).ldelim(),
            null != e.shardID &&
              Object.hasOwnProperty.call(e, 'shardID') &&
              $root.proto.ShardID.encode(e.shardID, o.uint32(82).fork()).ldelim(),
            null != e.realmID &&
              Object.hasOwnProperty.call(e, 'realmID') &&
              $root.proto.RealmID.encode(e.realmID, o.uint32(90).fork()).ldelim(),
            null != e.newRealmAdminKey &&
              Object.hasOwnProperty.call(e, 'newRealmAdminKey') &&
              $root.proto.Key.encode(e.newRealmAdminKey, o.uint32(98).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(106).string(e.memo),
            null != e.maxAutomaticTokenAssociations &&
              Object.hasOwnProperty.call(e, 'maxAutomaticTokenAssociations') &&
              o.uint32(112).int32(e.maxAutomaticTokenAssociations),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoCreateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.key = $root.proto.Key.decode(e, e.uint32());
                break;
              case 2:
                i.initialBalance = e.uint64();
                break;
              case 3:
                i.proxyAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 6:
                i.sendRecordThreshold = e.uint64();
                break;
              case 7:
                i.receiveRecordThreshold = e.uint64();
                break;
              case 8:
                i.receiverSigRequired = e.bool();
                break;
              case 9:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 10:
                i.shardID = $root.proto.ShardID.decode(e, e.uint32());
                break;
              case 11:
                i.realmID = $root.proto.RealmID.decode(e, e.uint32());
                break;
              case 12:
                i.newRealmAdminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 13:
                i.memo = e.string();
                break;
              case 14:
                i.maxAutomaticTokenAssociations = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoDeleteTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.transferAccountID = null),
        (e.prototype.deleteAccountID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.transferAccountID &&
              Object.hasOwnProperty.call(e, 'transferAccountID') &&
              $root.proto.AccountID.encode(e.transferAccountID, o.uint32(10).fork()).ldelim(),
            null != e.deleteAccountID &&
              Object.hasOwnProperty.call(e, 'deleteAccountID') &&
              $root.proto.AccountID.encode(e.deleteAccountID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoDeleteTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.transferAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.deleteAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoDeleteLiveHashTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.accountOfLiveHash = null),
        (e.prototype.liveHashToDelete = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.accountOfLiveHash &&
              Object.hasOwnProperty.call(e, 'accountOfLiveHash') &&
              $root.proto.AccountID.encode(e.accountOfLiveHash, o.uint32(10).fork()).ldelim(),
            null != e.liveHashToDelete &&
              Object.hasOwnProperty.call(e, 'liveHashToDelete') &&
              o.uint32(18).bytes(e.liveHashToDelete),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoDeleteLiveHashTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.accountOfLiveHash = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.liveHashToDelete = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoTransferTransactionBody: (function () {
      function e(e) {
        if (((this.tokenTransfers = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.transfers = null),
        (e.prototype.tokenTransfers = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.transfers &&
              Object.hasOwnProperty.call(e, 'transfers') &&
              $root.proto.TransferList.encode(e.transfers, o.uint32(10).fork()).ldelim(),
            null != e.tokenTransfers && e.tokenTransfers.length)
          )
            for (var t = 0; t < e.tokenTransfers.length; ++t)
              $root.proto.TokenTransferList.encode(e.tokenTransfers[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoTransferTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.transfers = $root.proto.TransferList.decode(e, e.uint32());
                break;
              case 2:
                (i.tokenTransfers && i.tokenTransfers.length) || (i.tokenTransfers = []),
                  i.tokenTransfers.push($root.proto.TokenTransferList.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoUpdateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.accountIDToUpdate = null),
        (e.prototype.key = null),
        (e.prototype.proxyAccountID = null),
        (e.prototype.proxyFraction = 0),
        (e.prototype.sendRecordThreshold = null),
        (e.prototype.sendRecordThresholdWrapper = null),
        (e.prototype.receiveRecordThreshold = null),
        (e.prototype.receiveRecordThresholdWrapper = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.expirationTime = null),
        (e.prototype.receiverSigRequired = null),
        (e.prototype.receiverSigRequiredWrapper = null),
        (e.prototype.memo = null),
        (e.prototype.maxAutomaticTokenAssociations = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'sendRecordThresholdField', {
          get: $util.oneOfGetter((o = ['sendRecordThreshold', 'sendRecordThresholdWrapper'])),
          set: $util.oneOfSetter(o),
        }),
        Object.defineProperty(e.prototype, 'receiveRecordThresholdField', {
          get: $util.oneOfGetter((o = ['receiveRecordThreshold', 'receiveRecordThresholdWrapper'])),
          set: $util.oneOfSetter(o),
        }),
        Object.defineProperty(e.prototype, 'receiverSigRequiredField', {
          get: $util.oneOfGetter((o = ['receiverSigRequired', 'receiverSigRequiredWrapper'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.accountIDToUpdate &&
              Object.hasOwnProperty.call(e, 'accountIDToUpdate') &&
              $root.proto.AccountID.encode(e.accountIDToUpdate, o.uint32(18).fork()).ldelim(),
            null != e.key &&
              Object.hasOwnProperty.call(e, 'key') &&
              $root.proto.Key.encode(e.key, o.uint32(26).fork()).ldelim(),
            null != e.proxyAccountID &&
              Object.hasOwnProperty.call(e, 'proxyAccountID') &&
              $root.proto.AccountID.encode(e.proxyAccountID, o.uint32(34).fork()).ldelim(),
            null != e.proxyFraction &&
              Object.hasOwnProperty.call(e, 'proxyFraction') &&
              o.uint32(40).int32(e.proxyFraction),
            null != e.sendRecordThreshold &&
              Object.hasOwnProperty.call(e, 'sendRecordThreshold') &&
              o.uint32(48).uint64(e.sendRecordThreshold),
            null != e.receiveRecordThreshold &&
              Object.hasOwnProperty.call(e, 'receiveRecordThreshold') &&
              o.uint32(56).uint64(e.receiveRecordThreshold),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(66).fork()).ldelim(),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.Timestamp.encode(e.expirationTime, o.uint32(74).fork()).ldelim(),
            null != e.receiverSigRequired &&
              Object.hasOwnProperty.call(e, 'receiverSigRequired') &&
              o.uint32(80).bool(e.receiverSigRequired),
            null != e.sendRecordThresholdWrapper &&
              Object.hasOwnProperty.call(e, 'sendRecordThresholdWrapper') &&
              $root.google.protobuf.UInt64Value.encode(e.sendRecordThresholdWrapper, o.uint32(90).fork()).ldelim(),
            null != e.receiveRecordThresholdWrapper &&
              Object.hasOwnProperty.call(e, 'receiveRecordThresholdWrapper') &&
              $root.google.protobuf.UInt64Value.encode(e.receiveRecordThresholdWrapper, o.uint32(98).fork()).ldelim(),
            null != e.receiverSigRequiredWrapper &&
              Object.hasOwnProperty.call(e, 'receiverSigRequiredWrapper') &&
              $root.google.protobuf.BoolValue.encode(e.receiverSigRequiredWrapper, o.uint32(106).fork()).ldelim(),
            null != e.memo &&
              Object.hasOwnProperty.call(e, 'memo') &&
              $root.google.protobuf.StringValue.encode(e.memo, o.uint32(114).fork()).ldelim(),
            null != e.maxAutomaticTokenAssociations &&
              Object.hasOwnProperty.call(e, 'maxAutomaticTokenAssociations') &&
              $root.google.protobuf.Int32Value.encode(e.maxAutomaticTokenAssociations, o.uint32(122).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoUpdateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 2:
                i.accountIDToUpdate = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.key = $root.proto.Key.decode(e, e.uint32());
                break;
              case 4:
                i.proxyAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 5:
                i.proxyFraction = e.int32();
                break;
              case 6:
                i.sendRecordThreshold = e.uint64();
                break;
              case 11:
                i.sendRecordThresholdWrapper = $root.google.protobuf.UInt64Value.decode(e, e.uint32());
                break;
              case 7:
                i.receiveRecordThreshold = e.uint64();
                break;
              case 12:
                i.receiveRecordThresholdWrapper = $root.google.protobuf.UInt64Value.decode(e, e.uint32());
                break;
              case 8:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 9:
                i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 10:
                i.receiverSigRequired = e.bool();
                break;
              case 13:
                i.receiverSigRequiredWrapper = $root.google.protobuf.BoolValue.decode(e, e.uint32());
                break;
              case 14:
                i.memo = $root.google.protobuf.StringValue.decode(e, e.uint32());
                break;
              case 15:
                i.maxAutomaticTokenAssociations = $root.google.protobuf.Int32Value.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileAppendTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.fileID = null),
        (e.prototype.contents = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(18).fork()).ldelim(),
            null != e.contents && Object.hasOwnProperty.call(e, 'contents') && o.uint32(34).bytes(e.contents),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileAppendTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 2:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 4:
                i.contents = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileCreateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.expirationTime = null),
        (e.prototype.keys = null),
        (e.prototype.contents = $util.newBuffer([])),
        (e.prototype.shardID = null),
        (e.prototype.realmID = null),
        (e.prototype.newRealmAdminKey = null),
        (e.prototype.memo = ''),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.Timestamp.encode(e.expirationTime, o.uint32(18).fork()).ldelim(),
            null != e.keys &&
              Object.hasOwnProperty.call(e, 'keys') &&
              $root.proto.KeyList.encode(e.keys, o.uint32(26).fork()).ldelim(),
            null != e.contents && Object.hasOwnProperty.call(e, 'contents') && o.uint32(34).bytes(e.contents),
            null != e.shardID &&
              Object.hasOwnProperty.call(e, 'shardID') &&
              $root.proto.ShardID.encode(e.shardID, o.uint32(42).fork()).ldelim(),
            null != e.realmID &&
              Object.hasOwnProperty.call(e, 'realmID') &&
              $root.proto.RealmID.encode(e.realmID, o.uint32(50).fork()).ldelim(),
            null != e.newRealmAdminKey &&
              Object.hasOwnProperty.call(e, 'newRealmAdminKey') &&
              $root.proto.Key.encode(e.newRealmAdminKey, o.uint32(58).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(66).string(e.memo),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileCreateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 2:
                i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 3:
                i.keys = $root.proto.KeyList.decode(e, e.uint32());
                break;
              case 4:
                i.contents = e.bytes();
                break;
              case 5:
                i.shardID = $root.proto.ShardID.decode(e, e.uint32());
                break;
              case 6:
                i.realmID = $root.proto.RealmID.decode(e, e.uint32());
                break;
              case 7:
                i.newRealmAdminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 8:
                i.memo = e.string();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileDeleteTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.fileID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileDeleteTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 2:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileUpdateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.fileID = null),
        (e.prototype.expirationTime = null),
        (e.prototype.keys = null),
        (e.prototype.contents = $util.newBuffer([])),
        (e.prototype.memo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(10).fork()).ldelim(),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.Timestamp.encode(e.expirationTime, o.uint32(18).fork()).ldelim(),
            null != e.keys &&
              Object.hasOwnProperty.call(e, 'keys') &&
              $root.proto.KeyList.encode(e.keys, o.uint32(26).fork()).ldelim(),
            null != e.contents && Object.hasOwnProperty.call(e, 'contents') && o.uint32(34).bytes(e.contents),
            null != e.memo &&
              Object.hasOwnProperty.call(e, 'memo') &&
              $root.google.protobuf.StringValue.encode(e.memo, o.uint32(42).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileUpdateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 2:
                i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 3:
                i.keys = $root.proto.KeyList.decode(e, e.uint32());
                break;
              case 4:
                i.contents = e.bytes();
                break;
              case 5:
                i.memo = $root.google.protobuf.StringValue.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractDeleteTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.contractID = null), (e.prototype.transferAccountID = null), (e.prototype.transferContractID = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'obtainers', {
          get: $util.oneOfGetter((o = ['transferAccountID', 'transferContractID'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(10).fork()).ldelim(),
            null != e.transferAccountID &&
              Object.hasOwnProperty.call(e, 'transferAccountID') &&
              $root.proto.AccountID.encode(e.transferAccountID, o.uint32(18).fork()).ldelim(),
            null != e.transferContractID &&
              Object.hasOwnProperty.call(e, 'transferContractID') &&
              $root.proto.ContractID.encode(e.transferContractID, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractDeleteTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 2:
                i.transferAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.transferContractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusUpdateTopicTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.topicID = null),
        (e.prototype.memo = null),
        (e.prototype.expirationTime = null),
        (e.prototype.adminKey = null),
        (e.prototype.submitKey = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.autoRenewAccount = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.topicID &&
              Object.hasOwnProperty.call(e, 'topicID') &&
              $root.proto.TopicID.encode(e.topicID, o.uint32(10).fork()).ldelim(),
            null != e.memo &&
              Object.hasOwnProperty.call(e, 'memo') &&
              $root.google.protobuf.StringValue.encode(e.memo, o.uint32(18).fork()).ldelim(),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.Timestamp.encode(e.expirationTime, o.uint32(34).fork()).ldelim(),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(50).fork()).ldelim(),
            null != e.submitKey &&
              Object.hasOwnProperty.call(e, 'submitKey') &&
              $root.proto.Key.encode(e.submitKey, o.uint32(58).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(66).fork()).ldelim(),
            null != e.autoRenewAccount &&
              Object.hasOwnProperty.call(e, 'autoRenewAccount') &&
              $root.proto.AccountID.encode(e.autoRenewAccount, o.uint32(74).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusUpdateTopicTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.topicID = $root.proto.TopicID.decode(e, e.uint32());
                break;
              case 2:
                i.memo = $root.google.protobuf.StringValue.decode(e, e.uint32());
                break;
              case 4:
                i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 6:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 7:
                i.submitKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 8:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 9:
                i.autoRenewAccount = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusMessageChunkInfo: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.initialTransactionID = null),
        (e.prototype.total = 0),
        (e.prototype.number = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.initialTransactionID &&
              Object.hasOwnProperty.call(e, 'initialTransactionID') &&
              $root.proto.TransactionID.encode(e.initialTransactionID, o.uint32(10).fork()).ldelim(),
            null != e.total && Object.hasOwnProperty.call(e, 'total') && o.uint32(16).int32(e.total),
            null != e.number && Object.hasOwnProperty.call(e, 'number') && o.uint32(24).int32(e.number),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusMessageChunkInfo(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.initialTransactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              case 2:
                i.total = e.int32();
                break;
              case 3:
                i.number = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusSubmitMessageTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.topicID = null),
        (e.prototype.message = $util.newBuffer([])),
        (e.prototype.chunkInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.topicID &&
              Object.hasOwnProperty.call(e, 'topicID') &&
              $root.proto.TopicID.encode(e.topicID, o.uint32(10).fork()).ldelim(),
            null != e.message && Object.hasOwnProperty.call(e, 'message') && o.uint32(18).bytes(e.message),
            null != e.chunkInfo &&
              Object.hasOwnProperty.call(e, 'chunkInfo') &&
              $root.proto.ConsensusMessageChunkInfo.encode(e.chunkInfo, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusSubmitMessageTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.topicID = $root.proto.TopicID.decode(e, e.uint32());
                break;
              case 2:
                i.message = e.bytes();
                break;
              case 3:
                i.chunkInfo = $root.proto.ConsensusMessageChunkInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    UncheckedSubmitBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.transactionBytes = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.transactionBytes &&
              Object.hasOwnProperty.call(e, 'transactionBytes') &&
              o.uint32(10).bytes(e.transactionBytes),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.UncheckedSubmitBody(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.transactionBytes = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenCreateTransactionBody: (function () {
      function e(e) {
        if (((this.customFees = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.name = ''),
        (e.prototype.symbol = ''),
        (e.prototype.decimals = 0),
        (e.prototype.initialSupply = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.treasury = null),
        (e.prototype.adminKey = null),
        (e.prototype.kycKey = null),
        (e.prototype.freezeKey = null),
        (e.prototype.wipeKey = null),
        (e.prototype.supplyKey = null),
        (e.prototype.freezeDefault = !1),
        (e.prototype.expiry = null),
        (e.prototype.autoRenewAccount = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.memo = ''),
        (e.prototype.tokenType = 0),
        (e.prototype.supplyType = 0),
        (e.prototype.maxSupply = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.feeScheduleKey = null),
        (e.prototype.customFees = $util.emptyArray),
        (e.prototype.pauseKey = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.name && Object.hasOwnProperty.call(e, 'name') && o.uint32(10).string(e.name),
            null != e.symbol && Object.hasOwnProperty.call(e, 'symbol') && o.uint32(18).string(e.symbol),
            null != e.decimals && Object.hasOwnProperty.call(e, 'decimals') && o.uint32(24).uint32(e.decimals),
            null != e.initialSupply &&
              Object.hasOwnProperty.call(e, 'initialSupply') &&
              o.uint32(32).uint64(e.initialSupply),
            null != e.treasury &&
              Object.hasOwnProperty.call(e, 'treasury') &&
              $root.proto.AccountID.encode(e.treasury, o.uint32(42).fork()).ldelim(),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(50).fork()).ldelim(),
            null != e.kycKey &&
              Object.hasOwnProperty.call(e, 'kycKey') &&
              $root.proto.Key.encode(e.kycKey, o.uint32(58).fork()).ldelim(),
            null != e.freezeKey &&
              Object.hasOwnProperty.call(e, 'freezeKey') &&
              $root.proto.Key.encode(e.freezeKey, o.uint32(66).fork()).ldelim(),
            null != e.wipeKey &&
              Object.hasOwnProperty.call(e, 'wipeKey') &&
              $root.proto.Key.encode(e.wipeKey, o.uint32(74).fork()).ldelim(),
            null != e.supplyKey &&
              Object.hasOwnProperty.call(e, 'supplyKey') &&
              $root.proto.Key.encode(e.supplyKey, o.uint32(82).fork()).ldelim(),
            null != e.freezeDefault &&
              Object.hasOwnProperty.call(e, 'freezeDefault') &&
              o.uint32(88).bool(e.freezeDefault),
            null != e.expiry &&
              Object.hasOwnProperty.call(e, 'expiry') &&
              $root.proto.Timestamp.encode(e.expiry, o.uint32(106).fork()).ldelim(),
            null != e.autoRenewAccount &&
              Object.hasOwnProperty.call(e, 'autoRenewAccount') &&
              $root.proto.AccountID.encode(e.autoRenewAccount, o.uint32(114).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(122).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(130).string(e.memo),
            null != e.tokenType && Object.hasOwnProperty.call(e, 'tokenType') && o.uint32(136).int32(e.tokenType),
            null != e.supplyType && Object.hasOwnProperty.call(e, 'supplyType') && o.uint32(144).int32(e.supplyType),
            null != e.maxSupply && Object.hasOwnProperty.call(e, 'maxSupply') && o.uint32(152).int64(e.maxSupply),
            null != e.feeScheduleKey &&
              Object.hasOwnProperty.call(e, 'feeScheduleKey') &&
              $root.proto.Key.encode(e.feeScheduleKey, o.uint32(162).fork()).ldelim(),
            null != e.customFees && e.customFees.length)
          )
            for (var t = 0; t < e.customFees.length; ++t)
              $root.proto.CustomFee.encode(e.customFees[t], o.uint32(170).fork()).ldelim();
          return (
            null != e.pauseKey &&
              Object.hasOwnProperty.call(e, 'pauseKey') &&
              $root.proto.Key.encode(e.pauseKey, o.uint32(178).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenCreateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.name = e.string();
                break;
              case 2:
                i.symbol = e.string();
                break;
              case 3:
                i.decimals = e.uint32();
                break;
              case 4:
                i.initialSupply = e.uint64();
                break;
              case 5:
                i.treasury = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 6:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 7:
                i.kycKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 8:
                i.freezeKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 9:
                i.wipeKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 10:
                i.supplyKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 11:
                i.freezeDefault = e.bool();
                break;
              case 13:
                i.expiry = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 14:
                i.autoRenewAccount = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 15:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 16:
                i.memo = e.string();
                break;
              case 17:
                i.tokenType = e.int32();
                break;
              case 18:
                i.supplyType = e.int32();
                break;
              case 19:
                i.maxSupply = e.int64();
                break;
              case 20:
                i.feeScheduleKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 21:
                (i.customFees && i.customFees.length) || (i.customFees = []),
                  i.customFees.push($root.proto.CustomFee.decode(e, e.uint32()));
                break;
              case 22:
                i.pauseKey = $root.proto.Key.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FractionalFee: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.fractionalAmount = null),
        (e.prototype.minimumAmount = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.maximumAmount = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.netOfTransfers = !1),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fractionalAmount &&
              Object.hasOwnProperty.call(e, 'fractionalAmount') &&
              $root.proto.Fraction.encode(e.fractionalAmount, o.uint32(10).fork()).ldelim(),
            null != e.minimumAmount &&
              Object.hasOwnProperty.call(e, 'minimumAmount') &&
              o.uint32(16).int64(e.minimumAmount),
            null != e.maximumAmount &&
              Object.hasOwnProperty.call(e, 'maximumAmount') &&
              o.uint32(24).int64(e.maximumAmount),
            null != e.netOfTransfers &&
              Object.hasOwnProperty.call(e, 'netOfTransfers') &&
              o.uint32(32).bool(e.netOfTransfers),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FractionalFee(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.fractionalAmount = $root.proto.Fraction.decode(e, e.uint32());
                break;
              case 2:
                i.minimumAmount = e.int64();
                break;
              case 3:
                i.maximumAmount = e.int64();
                break;
              case 4:
                i.netOfTransfers = e.bool();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FixedFee: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.denominatingTokenId = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(8).int64(e.amount),
            null != e.denominatingTokenId &&
              Object.hasOwnProperty.call(e, 'denominatingTokenId') &&
              $root.proto.TokenID.encode(e.denominatingTokenId, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FixedFee(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.amount = e.int64();
                break;
              case 2:
                i.denominatingTokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    RoyaltyFee: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.exchangeValueFraction = null),
        (e.prototype.fallbackFee = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.exchangeValueFraction &&
              Object.hasOwnProperty.call(e, 'exchangeValueFraction') &&
              $root.proto.Fraction.encode(e.exchangeValueFraction, o.uint32(10).fork()).ldelim(),
            null != e.fallbackFee &&
              Object.hasOwnProperty.call(e, 'fallbackFee') &&
              $root.proto.FixedFee.encode(e.fallbackFee, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.RoyaltyFee(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.exchangeValueFraction = $root.proto.Fraction.decode(e, e.uint32());
                break;
              case 2:
                i.fallbackFee = $root.proto.FixedFee.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CustomFee: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.fixedFee = null),
        (e.prototype.fractionalFee = null),
        (e.prototype.royaltyFee = null),
        (e.prototype.feeCollectorAccountId = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'fee', {
          get: $util.oneOfGetter((o = ['fixedFee', 'fractionalFee', 'royaltyFee'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.fixedFee &&
              Object.hasOwnProperty.call(e, 'fixedFee') &&
              $root.proto.FixedFee.encode(e.fixedFee, o.uint32(10).fork()).ldelim(),
            null != e.fractionalFee &&
              Object.hasOwnProperty.call(e, 'fractionalFee') &&
              $root.proto.FractionalFee.encode(e.fractionalFee, o.uint32(18).fork()).ldelim(),
            null != e.feeCollectorAccountId &&
              Object.hasOwnProperty.call(e, 'feeCollectorAccountId') &&
              $root.proto.AccountID.encode(e.feeCollectorAccountId, o.uint32(26).fork()).ldelim(),
            null != e.royaltyFee &&
              Object.hasOwnProperty.call(e, 'royaltyFee') &&
              $root.proto.RoyaltyFee.encode(e.royaltyFee, o.uint32(34).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CustomFee(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.fixedFee = $root.proto.FixedFee.decode(e, e.uint32());
                break;
              case 2:
                i.fractionalFee = $root.proto.FractionalFee.decode(e, e.uint32());
                break;
              case 4:
                i.royaltyFee = $root.proto.RoyaltyFee.decode(e, e.uint32());
                break;
              case 3:
                i.feeCollectorAccountId = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    AssessedCustomFee: (function () {
      function e(e) {
        if (((this.effectivePayerAccountId = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.tokenId = null),
        (e.prototype.feeCollectorAccountId = null),
        (e.prototype.effectivePayerAccountId = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(8).int64(e.amount),
            null != e.tokenId &&
              Object.hasOwnProperty.call(e, 'tokenId') &&
              $root.proto.TokenID.encode(e.tokenId, o.uint32(18).fork()).ldelim(),
            null != e.feeCollectorAccountId &&
              Object.hasOwnProperty.call(e, 'feeCollectorAccountId') &&
              $root.proto.AccountID.encode(e.feeCollectorAccountId, o.uint32(26).fork()).ldelim(),
            null != e.effectivePayerAccountId && e.effectivePayerAccountId.length)
          )
            for (var t = 0; t < e.effectivePayerAccountId.length; ++t)
              $root.proto.AccountID.encode(e.effectivePayerAccountId[t], o.uint32(34).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.AssessedCustomFee(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.amount = e.int64();
                break;
              case 2:
                i.tokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 3:
                i.feeCollectorAccountId = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 4:
                (i.effectivePayerAccountId && i.effectivePayerAccountId.length) || (i.effectivePayerAccountId = []),
                  i.effectivePayerAccountId.push($root.proto.AccountID.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenFreezeAccountTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.account = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.account &&
              Object.hasOwnProperty.call(e, 'account') &&
              $root.proto.AccountID.encode(e.account, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenFreezeAccountTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.account = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenUnfreezeAccountTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.account = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.account &&
              Object.hasOwnProperty.call(e, 'account') &&
              $root.proto.AccountID.encode(e.account, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenUnfreezeAccountTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.account = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGrantKycTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.account = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.account &&
              Object.hasOwnProperty.call(e, 'account') &&
              $root.proto.AccountID.encode(e.account, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGrantKycTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.account = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenRevokeKycTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.account = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.account &&
              Object.hasOwnProperty.call(e, 'account') &&
              $root.proto.AccountID.encode(e.account, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenRevokeKycTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.account = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenDeleteTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenDeleteTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenUpdateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.symbol = ''),
        (e.prototype.name = ''),
        (e.prototype.treasury = null),
        (e.prototype.adminKey = null),
        (e.prototype.kycKey = null),
        (e.prototype.freezeKey = null),
        (e.prototype.wipeKey = null),
        (e.prototype.supplyKey = null),
        (e.prototype.autoRenewAccount = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.expiry = null),
        (e.prototype.memo = null),
        (e.prototype.feeScheduleKey = null),
        (e.prototype.pauseKey = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.symbol && Object.hasOwnProperty.call(e, 'symbol') && o.uint32(18).string(e.symbol),
            null != e.name && Object.hasOwnProperty.call(e, 'name') && o.uint32(26).string(e.name),
            null != e.treasury &&
              Object.hasOwnProperty.call(e, 'treasury') &&
              $root.proto.AccountID.encode(e.treasury, o.uint32(34).fork()).ldelim(),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(42).fork()).ldelim(),
            null != e.kycKey &&
              Object.hasOwnProperty.call(e, 'kycKey') &&
              $root.proto.Key.encode(e.kycKey, o.uint32(50).fork()).ldelim(),
            null != e.freezeKey &&
              Object.hasOwnProperty.call(e, 'freezeKey') &&
              $root.proto.Key.encode(e.freezeKey, o.uint32(58).fork()).ldelim(),
            null != e.wipeKey &&
              Object.hasOwnProperty.call(e, 'wipeKey') &&
              $root.proto.Key.encode(e.wipeKey, o.uint32(66).fork()).ldelim(),
            null != e.supplyKey &&
              Object.hasOwnProperty.call(e, 'supplyKey') &&
              $root.proto.Key.encode(e.supplyKey, o.uint32(74).fork()).ldelim(),
            null != e.autoRenewAccount &&
              Object.hasOwnProperty.call(e, 'autoRenewAccount') &&
              $root.proto.AccountID.encode(e.autoRenewAccount, o.uint32(82).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(90).fork()).ldelim(),
            null != e.expiry &&
              Object.hasOwnProperty.call(e, 'expiry') &&
              $root.proto.Timestamp.encode(e.expiry, o.uint32(98).fork()).ldelim(),
            null != e.memo &&
              Object.hasOwnProperty.call(e, 'memo') &&
              $root.google.protobuf.StringValue.encode(e.memo, o.uint32(106).fork()).ldelim(),
            null != e.feeScheduleKey &&
              Object.hasOwnProperty.call(e, 'feeScheduleKey') &&
              $root.proto.Key.encode(e.feeScheduleKey, o.uint32(114).fork()).ldelim(),
            null != e.pauseKey &&
              Object.hasOwnProperty.call(e, 'pauseKey') &&
              $root.proto.Key.encode(e.pauseKey, o.uint32(122).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenUpdateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.symbol = e.string();
                break;
              case 3:
                i.name = e.string();
                break;
              case 4:
                i.treasury = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 5:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 6:
                i.kycKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 7:
                i.freezeKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 8:
                i.wipeKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 9:
                i.supplyKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 10:
                i.autoRenewAccount = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 11:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 12:
                i.expiry = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 13:
                i.memo = $root.google.protobuf.StringValue.decode(e, e.uint32());
                break;
              case 14:
                i.feeScheduleKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 15:
                i.pauseKey = $root.proto.Key.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenMintTransactionBody: (function () {
      function e(e) {
        if (((this.metadata = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.metadata = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(16).uint64(e.amount),
            null != e.metadata && e.metadata.length)
          )
            for (var t = 0; t < e.metadata.length; ++t) o.uint32(26).bytes(e.metadata[t]);
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenMintTransactionBody(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.amount = e.uint64();
                break;
              case 3:
                (i.metadata && i.metadata.length) || (i.metadata = []), i.metadata.push(e.bytes());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenBurnTransactionBody: (function () {
      function e(e) {
        if (((this.serialNumbers = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.serialNumbers = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(16).uint64(e.amount),
            null != e.serialNumbers && e.serialNumbers.length)
          ) {
            o.uint32(26).fork();
            for (var t = 0; t < e.serialNumbers.length; ++t) o.int64(e.serialNumbers[t]);
            o.ldelim();
          }
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenBurnTransactionBody(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.amount = e.uint64();
                break;
              case 3:
                if (((i.serialNumbers && i.serialNumbers.length) || (i.serialNumbers = []), 2 == (7 & d)))
                  for (var a = e.uint32() + e.pos; e.pos < a; ) i.serialNumbers.push(e.int64());
                else i.serialNumbers.push(e.int64());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenWipeAccountTransactionBody: (function () {
      function e(e) {
        if (((this.serialNumbers = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.prototype.account = null),
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.serialNumbers = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            null != e.account &&
              Object.hasOwnProperty.call(e, 'account') &&
              $root.proto.AccountID.encode(e.account, o.uint32(18).fork()).ldelim(),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(24).uint64(e.amount),
            null != e.serialNumbers && e.serialNumbers.length)
          ) {
            o.uint32(34).fork();
            for (var t = 0; t < e.serialNumbers.length; ++t) o.int64(e.serialNumbers[t]);
            o.ldelim();
          }
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenWipeAccountTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.account = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.amount = e.uint64();
                break;
              case 4:
                if (((i.serialNumbers && i.serialNumbers.length) || (i.serialNumbers = []), 2 == (7 & d)))
                  for (var a = e.uint32() + e.pos; e.pos < a; ) i.serialNumbers.push(e.int64());
                else i.serialNumbers.push(e.int64());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenAssociateTransactionBody: (function () {
      function e(e) {
        if (((this.tokens = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.account = null),
        (e.prototype.tokens = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.account &&
              Object.hasOwnProperty.call(e, 'account') &&
              $root.proto.AccountID.encode(e.account, o.uint32(10).fork()).ldelim(),
            null != e.tokens && e.tokens.length)
          )
            for (var t = 0; t < e.tokens.length; ++t)
              $root.proto.TokenID.encode(e.tokens[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenAssociateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.account = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                (i.tokens && i.tokens.length) || (i.tokens = []),
                  i.tokens.push($root.proto.TokenID.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenDissociateTransactionBody: (function () {
      function e(e) {
        if (((this.tokens = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.account = null),
        (e.prototype.tokens = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.account &&
              Object.hasOwnProperty.call(e, 'account') &&
              $root.proto.AccountID.encode(e.account, o.uint32(10).fork()).ldelim(),
            null != e.tokens && e.tokens.length)
          )
            for (var t = 0; t < e.tokens.length; ++t)
              $root.proto.TokenID.encode(e.tokens[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenDissociateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.account = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                (i.tokens && i.tokens.length) || (i.tokens = []),
                  i.tokens.push($root.proto.TokenID.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenFeeScheduleUpdateTransactionBody: (function () {
      function e(e) {
        if (((this.customFees = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenId = null),
        (e.prototype.customFees = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.tokenId &&
              Object.hasOwnProperty.call(e, 'tokenId') &&
              $root.proto.TokenID.encode(e.tokenId, o.uint32(10).fork()).ldelim(),
            null != e.customFees && e.customFees.length)
          )
            for (var t = 0; t < e.customFees.length; ++t)
              $root.proto.CustomFee.encode(e.customFees[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenFeeScheduleUpdateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.tokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                (i.customFees && i.customFees.length) || (i.customFees = []),
                  i.customFees.push($root.proto.CustomFee.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenPauseTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenPauseTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenUnpauseTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.token = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(10).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenUnpauseTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ScheduleCreateTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.scheduledTransactionBody = null),
        (e.prototype.memo = ''),
        (e.prototype.adminKey = null),
        (e.prototype.payerAccountID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.scheduledTransactionBody &&
              Object.hasOwnProperty.call(e, 'scheduledTransactionBody') &&
              $root.proto.SchedulableTransactionBody.encode(e.scheduledTransactionBody, o.uint32(10).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(18).string(e.memo),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(26).fork()).ldelim(),
            null != e.payerAccountID &&
              Object.hasOwnProperty.call(e, 'payerAccountID') &&
              $root.proto.AccountID.encode(e.payerAccountID, o.uint32(34).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ScheduleCreateTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.scheduledTransactionBody = $root.proto.SchedulableTransactionBody.decode(e, e.uint32());
                break;
              case 2:
                i.memo = e.string();
                break;
              case 3:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 4:
                i.payerAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    SchedulableTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.transactionFee = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.memo = ''),
        (e.prototype.contractCall = null),
        (e.prototype.contractCreateInstance = null),
        (e.prototype.contractUpdateInstance = null),
        (e.prototype.contractDeleteInstance = null),
        (e.prototype.cryptoCreateAccount = null),
        (e.prototype.cryptoDelete = null),
        (e.prototype.cryptoTransfer = null),
        (e.prototype.cryptoUpdateAccount = null),
        (e.prototype.fileAppend = null),
        (e.prototype.fileCreate = null),
        (e.prototype.fileDelete = null),
        (e.prototype.fileUpdate = null),
        (e.prototype.systemDelete = null),
        (e.prototype.systemUndelete = null),
        (e.prototype.freeze = null),
        (e.prototype.consensusCreateTopic = null),
        (e.prototype.consensusUpdateTopic = null),
        (e.prototype.consensusDeleteTopic = null),
        (e.prototype.consensusSubmitMessage = null),
        (e.prototype.tokenCreation = null),
        (e.prototype.tokenFreeze = null),
        (e.prototype.tokenUnfreeze = null),
        (e.prototype.tokenGrantKyc = null),
        (e.prototype.tokenRevokeKyc = null),
        (e.prototype.tokenDeletion = null),
        (e.prototype.tokenUpdate = null),
        (e.prototype.tokenMint = null),
        (e.prototype.tokenBurn = null),
        (e.prototype.tokenWipe = null),
        (e.prototype.tokenAssociate = null),
        (e.prototype.tokenDissociate = null),
        (e.prototype.tokenPause = null),
        (e.prototype.tokenUnpause = null),
        (e.prototype.scheduleDelete = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'data', {
          get: $util.oneOfGetter(
            (o = [
              'contractCall',
              'contractCreateInstance',
              'contractUpdateInstance',
              'contractDeleteInstance',
              'cryptoCreateAccount',
              'cryptoDelete',
              'cryptoTransfer',
              'cryptoUpdateAccount',
              'fileAppend',
              'fileCreate',
              'fileDelete',
              'fileUpdate',
              'systemDelete',
              'systemUndelete',
              'freeze',
              'consensusCreateTopic',
              'consensusUpdateTopic',
              'consensusDeleteTopic',
              'consensusSubmitMessage',
              'tokenCreation',
              'tokenFreeze',
              'tokenUnfreeze',
              'tokenGrantKyc',
              'tokenRevokeKyc',
              'tokenDeletion',
              'tokenUpdate',
              'tokenMint',
              'tokenBurn',
              'tokenWipe',
              'tokenAssociate',
              'tokenDissociate',
              'tokenPause',
              'tokenUnpause',
              'scheduleDelete',
            ])
          ),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.transactionFee &&
              Object.hasOwnProperty.call(e, 'transactionFee') &&
              o.uint32(8).uint64(e.transactionFee),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(18).string(e.memo),
            null != e.contractCall &&
              Object.hasOwnProperty.call(e, 'contractCall') &&
              $root.proto.ContractCallTransactionBody.encode(e.contractCall, o.uint32(26).fork()).ldelim(),
            null != e.contractCreateInstance &&
              Object.hasOwnProperty.call(e, 'contractCreateInstance') &&
              $root.proto.ContractCreateTransactionBody.encode(e.contractCreateInstance, o.uint32(34).fork()).ldelim(),
            null != e.contractUpdateInstance &&
              Object.hasOwnProperty.call(e, 'contractUpdateInstance') &&
              $root.proto.ContractUpdateTransactionBody.encode(e.contractUpdateInstance, o.uint32(42).fork()).ldelim(),
            null != e.contractDeleteInstance &&
              Object.hasOwnProperty.call(e, 'contractDeleteInstance') &&
              $root.proto.ContractDeleteTransactionBody.encode(e.contractDeleteInstance, o.uint32(50).fork()).ldelim(),
            null != e.cryptoCreateAccount &&
              Object.hasOwnProperty.call(e, 'cryptoCreateAccount') &&
              $root.proto.CryptoCreateTransactionBody.encode(e.cryptoCreateAccount, o.uint32(58).fork()).ldelim(),
            null != e.cryptoDelete &&
              Object.hasOwnProperty.call(e, 'cryptoDelete') &&
              $root.proto.CryptoDeleteTransactionBody.encode(e.cryptoDelete, o.uint32(66).fork()).ldelim(),
            null != e.cryptoTransfer &&
              Object.hasOwnProperty.call(e, 'cryptoTransfer') &&
              $root.proto.CryptoTransferTransactionBody.encode(e.cryptoTransfer, o.uint32(74).fork()).ldelim(),
            null != e.cryptoUpdateAccount &&
              Object.hasOwnProperty.call(e, 'cryptoUpdateAccount') &&
              $root.proto.CryptoUpdateTransactionBody.encode(e.cryptoUpdateAccount, o.uint32(82).fork()).ldelim(),
            null != e.fileAppend &&
              Object.hasOwnProperty.call(e, 'fileAppend') &&
              $root.proto.FileAppendTransactionBody.encode(e.fileAppend, o.uint32(90).fork()).ldelim(),
            null != e.fileCreate &&
              Object.hasOwnProperty.call(e, 'fileCreate') &&
              $root.proto.FileCreateTransactionBody.encode(e.fileCreate, o.uint32(98).fork()).ldelim(),
            null != e.fileDelete &&
              Object.hasOwnProperty.call(e, 'fileDelete') &&
              $root.proto.FileDeleteTransactionBody.encode(e.fileDelete, o.uint32(106).fork()).ldelim(),
            null != e.fileUpdate &&
              Object.hasOwnProperty.call(e, 'fileUpdate') &&
              $root.proto.FileUpdateTransactionBody.encode(e.fileUpdate, o.uint32(114).fork()).ldelim(),
            null != e.systemDelete &&
              Object.hasOwnProperty.call(e, 'systemDelete') &&
              $root.proto.SystemDeleteTransactionBody.encode(e.systemDelete, o.uint32(122).fork()).ldelim(),
            null != e.systemUndelete &&
              Object.hasOwnProperty.call(e, 'systemUndelete') &&
              $root.proto.SystemUndeleteTransactionBody.encode(e.systemUndelete, o.uint32(130).fork()).ldelim(),
            null != e.freeze &&
              Object.hasOwnProperty.call(e, 'freeze') &&
              $root.proto.FreezeTransactionBody.encode(e.freeze, o.uint32(138).fork()).ldelim(),
            null != e.consensusCreateTopic &&
              Object.hasOwnProperty.call(e, 'consensusCreateTopic') &&
              $root.proto.ConsensusCreateTopicTransactionBody.encode(
                e.consensusCreateTopic,
                o.uint32(146).fork()
              ).ldelim(),
            null != e.consensusUpdateTopic &&
              Object.hasOwnProperty.call(e, 'consensusUpdateTopic') &&
              $root.proto.ConsensusUpdateTopicTransactionBody.encode(
                e.consensusUpdateTopic,
                o.uint32(154).fork()
              ).ldelim(),
            null != e.consensusDeleteTopic &&
              Object.hasOwnProperty.call(e, 'consensusDeleteTopic') &&
              $root.proto.ConsensusDeleteTopicTransactionBody.encode(
                e.consensusDeleteTopic,
                o.uint32(162).fork()
              ).ldelim(),
            null != e.consensusSubmitMessage &&
              Object.hasOwnProperty.call(e, 'consensusSubmitMessage') &&
              $root.proto.ConsensusSubmitMessageTransactionBody.encode(
                e.consensusSubmitMessage,
                o.uint32(170).fork()
              ).ldelim(),
            null != e.tokenCreation &&
              Object.hasOwnProperty.call(e, 'tokenCreation') &&
              $root.proto.TokenCreateTransactionBody.encode(e.tokenCreation, o.uint32(178).fork()).ldelim(),
            null != e.tokenFreeze &&
              Object.hasOwnProperty.call(e, 'tokenFreeze') &&
              $root.proto.TokenFreezeAccountTransactionBody.encode(e.tokenFreeze, o.uint32(186).fork()).ldelim(),
            null != e.tokenUnfreeze &&
              Object.hasOwnProperty.call(e, 'tokenUnfreeze') &&
              $root.proto.TokenUnfreezeAccountTransactionBody.encode(e.tokenUnfreeze, o.uint32(194).fork()).ldelim(),
            null != e.tokenGrantKyc &&
              Object.hasOwnProperty.call(e, 'tokenGrantKyc') &&
              $root.proto.TokenGrantKycTransactionBody.encode(e.tokenGrantKyc, o.uint32(202).fork()).ldelim(),
            null != e.tokenRevokeKyc &&
              Object.hasOwnProperty.call(e, 'tokenRevokeKyc') &&
              $root.proto.TokenRevokeKycTransactionBody.encode(e.tokenRevokeKyc, o.uint32(210).fork()).ldelim(),
            null != e.tokenDeletion &&
              Object.hasOwnProperty.call(e, 'tokenDeletion') &&
              $root.proto.TokenDeleteTransactionBody.encode(e.tokenDeletion, o.uint32(218).fork()).ldelim(),
            null != e.tokenUpdate &&
              Object.hasOwnProperty.call(e, 'tokenUpdate') &&
              $root.proto.TokenUpdateTransactionBody.encode(e.tokenUpdate, o.uint32(226).fork()).ldelim(),
            null != e.tokenMint &&
              Object.hasOwnProperty.call(e, 'tokenMint') &&
              $root.proto.TokenMintTransactionBody.encode(e.tokenMint, o.uint32(234).fork()).ldelim(),
            null != e.tokenBurn &&
              Object.hasOwnProperty.call(e, 'tokenBurn') &&
              $root.proto.TokenBurnTransactionBody.encode(e.tokenBurn, o.uint32(242).fork()).ldelim(),
            null != e.tokenWipe &&
              Object.hasOwnProperty.call(e, 'tokenWipe') &&
              $root.proto.TokenWipeAccountTransactionBody.encode(e.tokenWipe, o.uint32(250).fork()).ldelim(),
            null != e.tokenAssociate &&
              Object.hasOwnProperty.call(e, 'tokenAssociate') &&
              $root.proto.TokenAssociateTransactionBody.encode(e.tokenAssociate, o.uint32(258).fork()).ldelim(),
            null != e.tokenDissociate &&
              Object.hasOwnProperty.call(e, 'tokenDissociate') &&
              $root.proto.TokenDissociateTransactionBody.encode(e.tokenDissociate, o.uint32(266).fork()).ldelim(),
            null != e.scheduleDelete &&
              Object.hasOwnProperty.call(e, 'scheduleDelete') &&
              $root.proto.ScheduleDeleteTransactionBody.encode(e.scheduleDelete, o.uint32(274).fork()).ldelim(),
            null != e.tokenPause &&
              Object.hasOwnProperty.call(e, 'tokenPause') &&
              $root.proto.TokenPauseTransactionBody.encode(e.tokenPause, o.uint32(282).fork()).ldelim(),
            null != e.tokenUnpause &&
              Object.hasOwnProperty.call(e, 'tokenUnpause') &&
              $root.proto.TokenUnpauseTransactionBody.encode(e.tokenUnpause, o.uint32(290).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SchedulableTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.transactionFee = e.uint64();
                break;
              case 2:
                i.memo = e.string();
                break;
              case 3:
                i.contractCall = $root.proto.ContractCallTransactionBody.decode(e, e.uint32());
                break;
              case 4:
                i.contractCreateInstance = $root.proto.ContractCreateTransactionBody.decode(e, e.uint32());
                break;
              case 5:
                i.contractUpdateInstance = $root.proto.ContractUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 6:
                i.contractDeleteInstance = $root.proto.ContractDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 7:
                i.cryptoCreateAccount = $root.proto.CryptoCreateTransactionBody.decode(e, e.uint32());
                break;
              case 8:
                i.cryptoDelete = $root.proto.CryptoDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 9:
                i.cryptoTransfer = $root.proto.CryptoTransferTransactionBody.decode(e, e.uint32());
                break;
              case 10:
                i.cryptoUpdateAccount = $root.proto.CryptoUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 11:
                i.fileAppend = $root.proto.FileAppendTransactionBody.decode(e, e.uint32());
                break;
              case 12:
                i.fileCreate = $root.proto.FileCreateTransactionBody.decode(e, e.uint32());
                break;
              case 13:
                i.fileDelete = $root.proto.FileDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 14:
                i.fileUpdate = $root.proto.FileUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 15:
                i.systemDelete = $root.proto.SystemDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 16:
                i.systemUndelete = $root.proto.SystemUndeleteTransactionBody.decode(e, e.uint32());
                break;
              case 17:
                i.freeze = $root.proto.FreezeTransactionBody.decode(e, e.uint32());
                break;
              case 18:
                i.consensusCreateTopic = $root.proto.ConsensusCreateTopicTransactionBody.decode(e, e.uint32());
                break;
              case 19:
                i.consensusUpdateTopic = $root.proto.ConsensusUpdateTopicTransactionBody.decode(e, e.uint32());
                break;
              case 20:
                i.consensusDeleteTopic = $root.proto.ConsensusDeleteTopicTransactionBody.decode(e, e.uint32());
                break;
              case 21:
                i.consensusSubmitMessage = $root.proto.ConsensusSubmitMessageTransactionBody.decode(e, e.uint32());
                break;
              case 22:
                i.tokenCreation = $root.proto.TokenCreateTransactionBody.decode(e, e.uint32());
                break;
              case 23:
                i.tokenFreeze = $root.proto.TokenFreezeAccountTransactionBody.decode(e, e.uint32());
                break;
              case 24:
                i.tokenUnfreeze = $root.proto.TokenUnfreezeAccountTransactionBody.decode(e, e.uint32());
                break;
              case 25:
                i.tokenGrantKyc = $root.proto.TokenGrantKycTransactionBody.decode(e, e.uint32());
                break;
              case 26:
                i.tokenRevokeKyc = $root.proto.TokenRevokeKycTransactionBody.decode(e, e.uint32());
                break;
              case 27:
                i.tokenDeletion = $root.proto.TokenDeleteTransactionBody.decode(e, e.uint32());
                break;
              case 28:
                i.tokenUpdate = $root.proto.TokenUpdateTransactionBody.decode(e, e.uint32());
                break;
              case 29:
                i.tokenMint = $root.proto.TokenMintTransactionBody.decode(e, e.uint32());
                break;
              case 30:
                i.tokenBurn = $root.proto.TokenBurnTransactionBody.decode(e, e.uint32());
                break;
              case 31:
                i.tokenWipe = $root.proto.TokenWipeAccountTransactionBody.decode(e, e.uint32());
                break;
              case 32:
                i.tokenAssociate = $root.proto.TokenAssociateTransactionBody.decode(e, e.uint32());
                break;
              case 33:
                i.tokenDissociate = $root.proto.TokenDissociateTransactionBody.decode(e, e.uint32());
                break;
              case 35:
                i.tokenPause = $root.proto.TokenPauseTransactionBody.decode(e, e.uint32());
                break;
              case 36:
                i.tokenUnpause = $root.proto.TokenUnpauseTransactionBody.decode(e, e.uint32());
                break;
              case 34:
                i.scheduleDelete = $root.proto.ScheduleDeleteTransactionBody.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ScheduleDeleteTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.scheduleID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.scheduleID &&
              Object.hasOwnProperty.call(e, 'scheduleID') &&
              $root.proto.ScheduleID.encode(e.scheduleID, o.uint32(10).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ScheduleDeleteTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.scheduleID = $root.proto.ScheduleID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ScheduleSignTransactionBody: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.scheduleID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.scheduleID &&
              Object.hasOwnProperty.call(e, 'scheduleID') &&
              $root.proto.ScheduleID.encode(e.scheduleID, o.uint32(10).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ScheduleSignTransactionBody(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.scheduleID = $root.proto.ScheduleID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ResponseHeader: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.nodeTransactionPrecheckCode = 0),
        (e.prototype.responseType = 0),
        (e.prototype.cost = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.stateProof = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.nodeTransactionPrecheckCode &&
              Object.hasOwnProperty.call(e, 'nodeTransactionPrecheckCode') &&
              o.uint32(8).int32(e.nodeTransactionPrecheckCode),
            null != e.responseType &&
              Object.hasOwnProperty.call(e, 'responseType') &&
              o.uint32(16).int32(e.responseType),
            null != e.cost && Object.hasOwnProperty.call(e, 'cost') && o.uint32(24).uint64(e.cost),
            null != e.stateProof && Object.hasOwnProperty.call(e, 'stateProof') && o.uint32(34).bytes(e.stateProof),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ResponseHeader(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.nodeTransactionPrecheckCode = e.int32();
                break;
              case 2:
                i.responseType = e.int32();
                break;
              case 3:
                i.cost = e.uint64();
                break;
              case 4:
                i.stateProof = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.nodeTransactionPrecheckCode = 0),
        (e.prototype.cost = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.nodeTransactionPrecheckCode &&
              Object.hasOwnProperty.call(e, 'nodeTransactionPrecheckCode') &&
              o.uint32(8).int32(e.nodeTransactionPrecheckCode),
            null != e.cost && Object.hasOwnProperty.call(e, 'cost') && o.uint32(16).uint64(e.cost),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.nodeTransactionPrecheckCode = e.int32();
                break;
              case 2:
                i.cost = e.uint64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ResponseCodeEnum: (function () {
      const e = {},
        o = Object.create(e);
      return (
        (o[(e[0] = 'OK')] = 0),
        (o[(e[1] = 'INVALID_TRANSACTION')] = 1),
        (o[(e[2] = 'PAYER_ACCOUNT_NOT_FOUND')] = 2),
        (o[(e[3] = 'INVALID_NODE_ACCOUNT')] = 3),
        (o[(e[4] = 'TRANSACTION_EXPIRED')] = 4),
        (o[(e[5] = 'INVALID_TRANSACTION_START')] = 5),
        (o[(e[6] = 'INVALID_TRANSACTION_DURATION')] = 6),
        (o[(e[7] = 'INVALID_SIGNATURE')] = 7),
        (o[(e[8] = 'MEMO_TOO_LONG')] = 8),
        (o[(e[9] = 'INSUFFICIENT_TX_FEE')] = 9),
        (o[(e[10] = 'INSUFFICIENT_PAYER_BALANCE')] = 10),
        (o[(e[11] = 'DUPLICATE_TRANSACTION')] = 11),
        (o[(e[12] = 'BUSY')] = 12),
        (o[(e[13] = 'NOT_SUPPORTED')] = 13),
        (o[(e[14] = 'INVALID_FILE_ID')] = 14),
        (o[(e[15] = 'INVALID_ACCOUNT_ID')] = 15),
        (o[(e[16] = 'INVALID_CONTRACT_ID')] = 16),
        (o[(e[17] = 'INVALID_TRANSACTION_ID')] = 17),
        (o[(e[18] = 'RECEIPT_NOT_FOUND')] = 18),
        (o[(e[19] = 'RECORD_NOT_FOUND')] = 19),
        (o[(e[20] = 'INVALID_SOLIDITY_ID')] = 20),
        (o[(e[21] = 'UNKNOWN')] = 21),
        (o[(e[22] = 'SUCCESS')] = 22),
        (o[(e[23] = 'FAIL_INVALID')] = 23),
        (o[(e[24] = 'FAIL_FEE')] = 24),
        (o[(e[25] = 'FAIL_BALANCE')] = 25),
        (o[(e[26] = 'KEY_REQUIRED')] = 26),
        (o[(e[27] = 'BAD_ENCODING')] = 27),
        (o[(e[28] = 'INSUFFICIENT_ACCOUNT_BALANCE')] = 28),
        (o[(e[29] = 'INVALID_SOLIDITY_ADDRESS')] = 29),
        (o[(e[30] = 'INSUFFICIENT_GAS')] = 30),
        (o[(e[31] = 'CONTRACT_SIZE_LIMIT_EXCEEDED')] = 31),
        (o[(e[32] = 'LOCAL_CALL_MODIFICATION_EXCEPTION')] = 32),
        (o[(e[33] = 'CONTRACT_REVERT_EXECUTED')] = 33),
        (o[(e[34] = 'CONTRACT_EXECUTION_EXCEPTION')] = 34),
        (o[(e[35] = 'INVALID_RECEIVING_NODE_ACCOUNT')] = 35),
        (o[(e[36] = 'MISSING_QUERY_HEADER')] = 36),
        (o[(e[37] = 'ACCOUNT_UPDATE_FAILED')] = 37),
        (o[(e[38] = 'INVALID_KEY_ENCODING')] = 38),
        (o[(e[39] = 'NULL_SOLIDITY_ADDRESS')] = 39),
        (o[(e[40] = 'CONTRACT_UPDATE_FAILED')] = 40),
        (o[(e[41] = 'INVALID_QUERY_HEADER')] = 41),
        (o[(e[42] = 'INVALID_FEE_SUBMITTED')] = 42),
        (o[(e[43] = 'INVALID_PAYER_SIGNATURE')] = 43),
        (o[(e[44] = 'KEY_NOT_PROVIDED')] = 44),
        (o[(e[45] = 'INVALID_EXPIRATION_TIME')] = 45),
        (o[(e[46] = 'NO_WACL_KEY')] = 46),
        (o[(e[47] = 'FILE_CONTENT_EMPTY')] = 47),
        (o[(e[48] = 'INVALID_ACCOUNT_AMOUNTS')] = 48),
        (o[(e[49] = 'EMPTY_TRANSACTION_BODY')] = 49),
        (o[(e[50] = 'INVALID_TRANSACTION_BODY')] = 50),
        (o[(e[51] = 'INVALID_SIGNATURE_TYPE_MISMATCHING_KEY')] = 51),
        (o[(e[52] = 'INVALID_SIGNATURE_COUNT_MISMATCHING_KEY')] = 52),
        (o[(e[53] = 'EMPTY_LIVE_HASH_BODY')] = 53),
        (o[(e[54] = 'EMPTY_LIVE_HASH')] = 54),
        (o[(e[55] = 'EMPTY_LIVE_HASH_KEYS')] = 55),
        (o[(e[56] = 'INVALID_LIVE_HASH_SIZE')] = 56),
        (o[(e[57] = 'EMPTY_QUERY_BODY')] = 57),
        (o[(e[58] = 'EMPTY_LIVE_HASH_QUERY')] = 58),
        (o[(e[59] = 'LIVE_HASH_NOT_FOUND')] = 59),
        (o[(e[60] = 'ACCOUNT_ID_DOES_NOT_EXIST')] = 60),
        (o[(e[61] = 'LIVE_HASH_ALREADY_EXISTS')] = 61),
        (o[(e[62] = 'INVALID_FILE_WACL')] = 62),
        (o[(e[63] = 'SERIALIZATION_FAILED')] = 63),
        (o[(e[64] = 'TRANSACTION_OVERSIZE')] = 64),
        (o[(e[65] = 'TRANSACTION_TOO_MANY_LAYERS')] = 65),
        (o[(e[66] = 'CONTRACT_DELETED')] = 66),
        (o[(e[67] = 'PLATFORM_NOT_ACTIVE')] = 67),
        (o[(e[68] = 'KEY_PREFIX_MISMATCH')] = 68),
        (o[(e[69] = 'PLATFORM_TRANSACTION_NOT_CREATED')] = 69),
        (o[(e[70] = 'INVALID_RENEWAL_PERIOD')] = 70),
        (o[(e[71] = 'INVALID_PAYER_ACCOUNT_ID')] = 71),
        (o[(e[72] = 'ACCOUNT_DELETED')] = 72),
        (o[(e[73] = 'FILE_DELETED')] = 73),
        (o[(e[74] = 'ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS')] = 74),
        (o[(e[75] = 'SETTING_NEGATIVE_ACCOUNT_BALANCE')] = 75),
        (o[(e[76] = 'OBTAINER_REQUIRED')] = 76),
        (o[(e[77] = 'OBTAINER_SAME_CONTRACT_ID')] = 77),
        (o[(e[78] = 'OBTAINER_DOES_NOT_EXIST')] = 78),
        (o[(e[79] = 'MODIFYING_IMMUTABLE_CONTRACT')] = 79),
        (o[(e[80] = 'FILE_SYSTEM_EXCEPTION')] = 80),
        (o[(e[81] = 'AUTORENEW_DURATION_NOT_IN_RANGE')] = 81),
        (o[(e[82] = 'ERROR_DECODING_BYTESTRING')] = 82),
        (o[(e[83] = 'CONTRACT_FILE_EMPTY')] = 83),
        (o[(e[84] = 'CONTRACT_BYTECODE_EMPTY')] = 84),
        (o[(e[85] = 'INVALID_INITIAL_BALANCE')] = 85),
        (o[(e[86] = 'INVALID_RECEIVE_RECORD_THRESHOLD')] = 86),
        (o[(e[87] = 'INVALID_SEND_RECORD_THRESHOLD')] = 87),
        (o[(e[88] = 'ACCOUNT_IS_NOT_GENESIS_ACCOUNT')] = 88),
        (o[(e[89] = 'PAYER_ACCOUNT_UNAUTHORIZED')] = 89),
        (o[(e[90] = 'INVALID_FREEZE_TRANSACTION_BODY')] = 90),
        (o[(e[91] = 'FREEZE_TRANSACTION_BODY_NOT_FOUND')] = 91),
        (o[(e[92] = 'TRANSFER_LIST_SIZE_LIMIT_EXCEEDED')] = 92),
        (o[(e[93] = 'RESULT_SIZE_LIMIT_EXCEEDED')] = 93),
        (o[(e[94] = 'NOT_SPECIAL_ACCOUNT')] = 94),
        (o[(e[95] = 'CONTRACT_NEGATIVE_GAS')] = 95),
        (o[(e[96] = 'CONTRACT_NEGATIVE_VALUE')] = 96),
        (o[(e[97] = 'INVALID_FEE_FILE')] = 97),
        (o[(e[98] = 'INVALID_EXCHANGE_RATE_FILE')] = 98),
        (o[(e[99] = 'INSUFFICIENT_LOCAL_CALL_GAS')] = 99),
        (o[(e[100] = 'ENTITY_NOT_ALLOWED_TO_DELETE')] = 100),
        (o[(e[101] = 'AUTHORIZATION_FAILED')] = 101),
        (o[(e[102] = 'FILE_UPLOADED_PROTO_INVALID')] = 102),
        (o[(e[103] = 'FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK')] = 103),
        (o[(e[104] = 'FEE_SCHEDULE_FILE_PART_UPLOADED')] = 104),
        (o[(e[105] = 'EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED')] = 105),
        (o[(e[106] = 'MAX_CONTRACT_STORAGE_EXCEEDED')] = 106),
        (o[(e[107] = 'TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT')] = 107),
        (o[(e[108] = 'TOTAL_LEDGER_BALANCE_INVALID')] = 108),
        (o[(e[110] = 'EXPIRATION_REDUCTION_NOT_ALLOWED')] = 110),
        (o[(e[111] = 'MAX_GAS_LIMIT_EXCEEDED')] = 111),
        (o[(e[112] = 'MAX_FILE_SIZE_EXCEEDED')] = 112),
        (o[(e[113] = 'RECEIVER_SIG_REQUIRED')] = 113),
        (o[(e[150] = 'INVALID_TOPIC_ID')] = 150),
        (o[(e[155] = 'INVALID_ADMIN_KEY')] = 155),
        (o[(e[156] = 'INVALID_SUBMIT_KEY')] = 156),
        (o[(e[157] = 'UNAUTHORIZED')] = 157),
        (o[(e[158] = 'INVALID_TOPIC_MESSAGE')] = 158),
        (o[(e[159] = 'INVALID_AUTORENEW_ACCOUNT')] = 159),
        (o[(e[160] = 'AUTORENEW_ACCOUNT_NOT_ALLOWED')] = 160),
        (o[(e[162] = 'TOPIC_EXPIRED')] = 162),
        (o[(e[163] = 'INVALID_CHUNK_NUMBER')] = 163),
        (o[(e[164] = 'INVALID_CHUNK_TRANSACTION_ID')] = 164),
        (o[(e[165] = 'ACCOUNT_FROZEN_FOR_TOKEN')] = 165),
        (o[(e[166] = 'TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED')] = 166),
        (o[(e[167] = 'INVALID_TOKEN_ID')] = 167),
        (o[(e[168] = 'INVALID_TOKEN_DECIMALS')] = 168),
        (o[(e[169] = 'INVALID_TOKEN_INITIAL_SUPPLY')] = 169),
        (o[(e[170] = 'INVALID_TREASURY_ACCOUNT_FOR_TOKEN')] = 170),
        (o[(e[171] = 'INVALID_TOKEN_SYMBOL')] = 171),
        (o[(e[172] = 'TOKEN_HAS_NO_FREEZE_KEY')] = 172),
        (o[(e[173] = 'TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN')] = 173),
        (o[(e[174] = 'MISSING_TOKEN_SYMBOL')] = 174),
        (o[(e[175] = 'TOKEN_SYMBOL_TOO_LONG')] = 175),
        (o[(e[176] = 'ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN')] = 176),
        (o[(e[177] = 'TOKEN_HAS_NO_KYC_KEY')] = 177),
        (o[(e[178] = 'INSUFFICIENT_TOKEN_BALANCE')] = 178),
        (o[(e[179] = 'TOKEN_WAS_DELETED')] = 179),
        (o[(e[180] = 'TOKEN_HAS_NO_SUPPLY_KEY')] = 180),
        (o[(e[181] = 'TOKEN_HAS_NO_WIPE_KEY')] = 181),
        (o[(e[182] = 'INVALID_TOKEN_MINT_AMOUNT')] = 182),
        (o[(e[183] = 'INVALID_TOKEN_BURN_AMOUNT')] = 183),
        (o[(e[184] = 'TOKEN_NOT_ASSOCIATED_TO_ACCOUNT')] = 184),
        (o[(e[185] = 'CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT')] = 185),
        (o[(e[186] = 'INVALID_KYC_KEY')] = 186),
        (o[(e[187] = 'INVALID_WIPE_KEY')] = 187),
        (o[(e[188] = 'INVALID_FREEZE_KEY')] = 188),
        (o[(e[189] = 'INVALID_SUPPLY_KEY')] = 189),
        (o[(e[190] = 'MISSING_TOKEN_NAME')] = 190),
        (o[(e[191] = 'TOKEN_NAME_TOO_LONG')] = 191),
        (o[(e[192] = 'INVALID_WIPING_AMOUNT')] = 192),
        (o[(e[193] = 'TOKEN_IS_IMMUTABLE')] = 193),
        (o[(e[194] = 'TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT')] = 194),
        (o[(e[195] = 'TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES')] = 195),
        (o[(e[196] = 'ACCOUNT_IS_TREASURY')] = 196),
        (o[(e[197] = 'TOKEN_ID_REPEATED_IN_TOKEN_LIST')] = 197),
        (o[(e[198] = 'TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED')] = 198),
        (o[(e[199] = 'EMPTY_TOKEN_TRANSFER_BODY')] = 199),
        (o[(e[200] = 'EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS')] = 200),
        (o[(e[201] = 'INVALID_SCHEDULE_ID')] = 201),
        (o[(e[202] = 'SCHEDULE_IS_IMMUTABLE')] = 202),
        (o[(e[203] = 'INVALID_SCHEDULE_PAYER_ID')] = 203),
        (o[(e[204] = 'INVALID_SCHEDULE_ACCOUNT_ID')] = 204),
        (o[(e[205] = 'NO_NEW_VALID_SIGNATURES')] = 205),
        (o[(e[206] = 'UNRESOLVABLE_REQUIRED_SIGNERS')] = 206),
        (o[(e[207] = 'SCHEDULED_TRANSACTION_NOT_IN_WHITELIST')] = 207),
        (o[(e[208] = 'SOME_SIGNATURES_WERE_INVALID')] = 208),
        (o[(e[209] = 'TRANSACTION_ID_FIELD_NOT_ALLOWED')] = 209),
        (o[(e[210] = 'IDENTICAL_SCHEDULE_ALREADY_CREATED')] = 210),
        (o[(e[211] = 'INVALID_ZERO_BYTE_IN_STRING')] = 211),
        (o[(e[212] = 'SCHEDULE_ALREADY_DELETED')] = 212),
        (o[(e[213] = 'SCHEDULE_ALREADY_EXECUTED')] = 213),
        (o[(e[214] = 'MESSAGE_SIZE_TOO_LARGE')] = 214),
        (o[(e[215] = 'OPERATION_REPEATED_IN_BUCKET_GROUPS')] = 215),
        (o[(e[216] = 'BUCKET_CAPACITY_OVERFLOW')] = 216),
        (o[(e[217] = 'NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION')] = 217),
        (o[(e[218] = 'BUCKET_HAS_NO_THROTTLE_GROUPS')] = 218),
        (o[(e[219] = 'THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC')] = 219),
        (o[(e[220] = 'SUCCESS_BUT_MISSING_EXPECTED_OPERATION')] = 220),
        (o[(e[221] = 'UNPARSEABLE_THROTTLE_DEFINITIONS')] = 221),
        (o[(e[222] = 'INVALID_THROTTLE_DEFINITIONS')] = 222),
        (o[(e[223] = 'ACCOUNT_EXPIRED_AND_PENDING_REMOVAL')] = 223),
        (o[(e[224] = 'INVALID_TOKEN_MAX_SUPPLY')] = 224),
        (o[(e[225] = 'INVALID_TOKEN_NFT_SERIAL_NUMBER')] = 225),
        (o[(e[226] = 'INVALID_NFT_ID')] = 226),
        (o[(e[227] = 'METADATA_TOO_LONG')] = 227),
        (o[(e[228] = 'BATCH_SIZE_LIMIT_EXCEEDED')] = 228),
        (o[(e[229] = 'INVALID_QUERY_RANGE')] = 229),
        (o[(e[230] = 'FRACTION_DIVIDES_BY_ZERO')] = 230),
        (o[(e[231] = 'INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE')] = 231),
        (o[(e[232] = 'CUSTOM_FEES_LIST_TOO_LONG')] = 232),
        (o[(e[233] = 'INVALID_CUSTOM_FEE_COLLECTOR')] = 233),
        (o[(e[234] = 'INVALID_TOKEN_ID_IN_CUSTOM_FEES')] = 234),
        (o[(e[235] = 'TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR')] = 235),
        (o[(e[236] = 'TOKEN_MAX_SUPPLY_REACHED')] = 236),
        (o[(e[237] = 'SENDER_DOES_NOT_OWN_NFT_SERIAL_NO')] = 237),
        (o[(e[238] = 'CUSTOM_FEE_NOT_FULLY_SPECIFIED')] = 238),
        (o[(e[239] = 'CUSTOM_FEE_MUST_BE_POSITIVE')] = 239),
        (o[(e[240] = 'TOKEN_HAS_NO_FEE_SCHEDULE_KEY')] = 240),
        (o[(e[241] = 'CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE')] = 241),
        (o[(e[242] = 'ROYALTY_FRACTION_CANNOT_EXCEED_ONE')] = 242),
        (o[(e[243] = 'FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT')] = 243),
        (o[(e[244] = 'CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES')] = 244),
        (o[(e[245] = 'CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON')] = 245),
        (o[(e[246] = 'CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON')] = 246),
        (o[(e[247] = 'INVALID_CUSTOM_FEE_SCHEDULE_KEY')] = 247),
        (o[(e[248] = 'INVALID_TOKEN_MINT_METADATA')] = 248),
        (o[(e[249] = 'INVALID_TOKEN_BURN_METADATA')] = 249),
        (o[(e[250] = 'CURRENT_TREASURY_STILL_OWNS_NFTS')] = 250),
        (o[(e[251] = 'ACCOUNT_STILL_OWNS_NFTS')] = 251),
        (o[(e[252] = 'TREASURY_MUST_OWN_BURNED_NFT')] = 252),
        (o[(e[253] = 'ACCOUNT_DOES_NOT_OWN_WIPED_NFT')] = 253),
        (o[(e[254] = 'ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON')] = 254),
        (o[(e[255] = 'MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED')] = 255),
        (o[(e[256] = 'PAYER_ACCOUNT_DELETED')] = 256),
        (o[(e[257] = 'CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH')] = 257),
        (o[(e[258] = 'CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS')] = 258),
        (o[(e[259] = 'INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE')] = 259),
        (o[(e[260] = 'SERIAL_NUMBER_LIMIT_REACHED')] = 260),
        (o[(e[261] = 'CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE')] = 261),
        (o[(e[262] = 'NO_REMAINING_AUTOMATIC_ASSOCIATIONS')] = 262),
        (o[(e[263] = 'EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT')] = 263),
        (o[(e[264] = 'REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT')] = 264),
        (o[(e[265] = 'TOKEN_IS_PAUSED')] = 265),
        (o[(e[266] = 'TOKEN_HAS_NO_PAUSE_KEY')] = 266),
        (o[(e[267] = 'INVALID_PAUSE_KEY')] = 267),
        (o[(e[268] = 'FREEZE_UPDATE_FILE_DOES_NOT_EXIST')] = 268),
        (o[(e[269] = 'FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH')] = 269),
        (o[(e[270] = 'NO_UPGRADE_HAS_BEEN_PREPARED')] = 270),
        (o[(e[271] = 'NO_FREEZE_IS_SCHEDULED')] = 271),
        (o[(e[272] = 'UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE')] = 272),
        (o[(e[273] = 'FREEZE_START_TIME_MUST_BE_FUTURE')] = 273),
        (o[(e[274] = 'PREPARED_UPDATE_FILE_IS_IMMUTABLE')] = 274),
        (o[(e[275] = 'FREEZE_ALREADY_SCHEDULED')] = 275),
        (o[(e[276] = 'FREEZE_UPGRADE_IN_PROGRESS')] = 276),
        (o[(e[277] = 'UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED')] = 277),
        (o[(e[278] = 'UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED')] = 278),
        (o[(e[279] = 'CONSENSUS_GAS_EXHAUSTED')] = 279),
        (o[(e[280] = 'REVERTED_SUCCESS')] = 280),
        (o[(e[281] = 'MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED')] = 281),
        (o[(e[282] = 'INVALID_ALIAS_KEY')] = 282),
        o
      );
    })(),
    ConsensusTopicInfo: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.memo = ''),
        (e.prototype.runningHash = $util.newBuffer([])),
        (e.prototype.sequenceNumber = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.expirationTime = null),
        (e.prototype.adminKey = null),
        (e.prototype.submitKey = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.autoRenewAccount = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(10).string(e.memo),
            null != e.runningHash && Object.hasOwnProperty.call(e, 'runningHash') && o.uint32(18).bytes(e.runningHash),
            null != e.sequenceNumber &&
              Object.hasOwnProperty.call(e, 'sequenceNumber') &&
              o.uint32(24).uint64(e.sequenceNumber),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.Timestamp.encode(e.expirationTime, o.uint32(34).fork()).ldelim(),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(42).fork()).ldelim(),
            null != e.submitKey &&
              Object.hasOwnProperty.call(e, 'submitKey') &&
              $root.proto.Key.encode(e.submitKey, o.uint32(50).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(58).fork()).ldelim(),
            null != e.autoRenewAccount &&
              Object.hasOwnProperty.call(e, 'autoRenewAccount') &&
              $root.proto.AccountID.encode(e.autoRenewAccount, o.uint32(66).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusTopicInfo(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.memo = e.string();
                break;
              case 2:
                i.runningHash = e.bytes();
                break;
              case 3:
                i.sequenceNumber = e.uint64();
                break;
              case 4:
                i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 5:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 6:
                i.submitKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 7:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 8:
                i.autoRenewAccount = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.createTopic = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'createTopic'}
        ),
        Object.defineProperty(
          (e.prototype.updateTopic = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'updateTopic'}
        ),
        Object.defineProperty(
          (e.prototype.deleteTopic = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'deleteTopic'}
        ),
        Object.defineProperty(
          (e.prototype.getTopicInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getTopicInfo'}
        ),
        Object.defineProperty(
          (e.prototype.submitMessage = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'submitMessage'}
        ),
        e
      );
    })(),
    Query: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.getByKey = null),
        (e.prototype.getBySolidityID = null),
        (e.prototype.contractCallLocal = null),
        (e.prototype.contractGetInfo = null),
        (e.prototype.contractGetBytecode = null),
        (e.prototype.ContractGetRecords = null),
        (e.prototype.cryptogetAccountBalance = null),
        (e.prototype.cryptoGetAccountRecords = null),
        (e.prototype.cryptoGetInfo = null),
        (e.prototype.cryptoGetLiveHash = null),
        (e.prototype.cryptoGetProxyStakers = null),
        (e.prototype.fileGetContents = null),
        (e.prototype.fileGetInfo = null),
        (e.prototype.transactionGetReceipt = null),
        (e.prototype.transactionGetRecord = null),
        (e.prototype.transactionGetFastRecord = null),
        (e.prototype.consensusGetTopicInfo = null),
        (e.prototype.networkGetVersionInfo = null),
        (e.prototype.tokenGetInfo = null),
        (e.prototype.scheduleGetInfo = null),
        (e.prototype.tokenGetAccountNftInfos = null),
        (e.prototype.tokenGetNftInfo = null),
        (e.prototype.tokenGetNftInfos = null),
        (e.prototype.networkGetExecutionTime = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'query', {
          get: $util.oneOfGetter(
            (o = [
              'getByKey',
              'getBySolidityID',
              'contractCallLocal',
              'contractGetInfo',
              'contractGetBytecode',
              'ContractGetRecords',
              'cryptogetAccountBalance',
              'cryptoGetAccountRecords',
              'cryptoGetInfo',
              'cryptoGetLiveHash',
              'cryptoGetProxyStakers',
              'fileGetContents',
              'fileGetInfo',
              'transactionGetReceipt',
              'transactionGetRecord',
              'transactionGetFastRecord',
              'consensusGetTopicInfo',
              'networkGetVersionInfo',
              'tokenGetInfo',
              'scheduleGetInfo',
              'tokenGetAccountNftInfos',
              'tokenGetNftInfo',
              'tokenGetNftInfos',
              'networkGetExecutionTime',
            ])
          ),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.getByKey &&
              Object.hasOwnProperty.call(e, 'getByKey') &&
              $root.proto.GetByKeyQuery.encode(e.getByKey, o.uint32(10).fork()).ldelim(),
            null != e.getBySolidityID &&
              Object.hasOwnProperty.call(e, 'getBySolidityID') &&
              $root.proto.GetBySolidityIDQuery.encode(e.getBySolidityID, o.uint32(18).fork()).ldelim(),
            null != e.contractCallLocal &&
              Object.hasOwnProperty.call(e, 'contractCallLocal') &&
              $root.proto.ContractCallLocalQuery.encode(e.contractCallLocal, o.uint32(26).fork()).ldelim(),
            null != e.contractGetInfo &&
              Object.hasOwnProperty.call(e, 'contractGetInfo') &&
              $root.proto.ContractGetInfoQuery.encode(e.contractGetInfo, o.uint32(34).fork()).ldelim(),
            null != e.contractGetBytecode &&
              Object.hasOwnProperty.call(e, 'contractGetBytecode') &&
              $root.proto.ContractGetBytecodeQuery.encode(e.contractGetBytecode, o.uint32(42).fork()).ldelim(),
            null != e.ContractGetRecords &&
              Object.hasOwnProperty.call(e, 'ContractGetRecords') &&
              $root.proto.ContractGetRecordsQuery.encode(e.ContractGetRecords, o.uint32(50).fork()).ldelim(),
            null != e.cryptogetAccountBalance &&
              Object.hasOwnProperty.call(e, 'cryptogetAccountBalance') &&
              $root.proto.CryptoGetAccountBalanceQuery.encode(e.cryptogetAccountBalance, o.uint32(58).fork()).ldelim(),
            null != e.cryptoGetAccountRecords &&
              Object.hasOwnProperty.call(e, 'cryptoGetAccountRecords') &&
              $root.proto.CryptoGetAccountRecordsQuery.encode(e.cryptoGetAccountRecords, o.uint32(66).fork()).ldelim(),
            null != e.cryptoGetInfo &&
              Object.hasOwnProperty.call(e, 'cryptoGetInfo') &&
              $root.proto.CryptoGetInfoQuery.encode(e.cryptoGetInfo, o.uint32(74).fork()).ldelim(),
            null != e.cryptoGetLiveHash &&
              Object.hasOwnProperty.call(e, 'cryptoGetLiveHash') &&
              $root.proto.CryptoGetLiveHashQuery.encode(e.cryptoGetLiveHash, o.uint32(82).fork()).ldelim(),
            null != e.cryptoGetProxyStakers &&
              Object.hasOwnProperty.call(e, 'cryptoGetProxyStakers') &&
              $root.proto.CryptoGetStakersQuery.encode(e.cryptoGetProxyStakers, o.uint32(90).fork()).ldelim(),
            null != e.fileGetContents &&
              Object.hasOwnProperty.call(e, 'fileGetContents') &&
              $root.proto.FileGetContentsQuery.encode(e.fileGetContents, o.uint32(98).fork()).ldelim(),
            null != e.fileGetInfo &&
              Object.hasOwnProperty.call(e, 'fileGetInfo') &&
              $root.proto.FileGetInfoQuery.encode(e.fileGetInfo, o.uint32(106).fork()).ldelim(),
            null != e.transactionGetReceipt &&
              Object.hasOwnProperty.call(e, 'transactionGetReceipt') &&
              $root.proto.TransactionGetReceiptQuery.encode(e.transactionGetReceipt, o.uint32(114).fork()).ldelim(),
            null != e.transactionGetRecord &&
              Object.hasOwnProperty.call(e, 'transactionGetRecord') &&
              $root.proto.TransactionGetRecordQuery.encode(e.transactionGetRecord, o.uint32(122).fork()).ldelim(),
            null != e.transactionGetFastRecord &&
              Object.hasOwnProperty.call(e, 'transactionGetFastRecord') &&
              $root.proto.TransactionGetFastRecordQuery.encode(
                e.transactionGetFastRecord,
                o.uint32(130).fork()
              ).ldelim(),
            null != e.consensusGetTopicInfo &&
              Object.hasOwnProperty.call(e, 'consensusGetTopicInfo') &&
              $root.proto.ConsensusGetTopicInfoQuery.encode(e.consensusGetTopicInfo, o.uint32(402).fork()).ldelim(),
            null != e.networkGetVersionInfo &&
              Object.hasOwnProperty.call(e, 'networkGetVersionInfo') &&
              $root.proto.NetworkGetVersionInfoQuery.encode(e.networkGetVersionInfo, o.uint32(410).fork()).ldelim(),
            null != e.tokenGetInfo &&
              Object.hasOwnProperty.call(e, 'tokenGetInfo') &&
              $root.proto.TokenGetInfoQuery.encode(e.tokenGetInfo, o.uint32(418).fork()).ldelim(),
            null != e.scheduleGetInfo &&
              Object.hasOwnProperty.call(e, 'scheduleGetInfo') &&
              $root.proto.ScheduleGetInfoQuery.encode(e.scheduleGetInfo, o.uint32(426).fork()).ldelim(),
            null != e.tokenGetAccountNftInfos &&
              Object.hasOwnProperty.call(e, 'tokenGetAccountNftInfos') &&
              $root.proto.TokenGetAccountNftInfosQuery.encode(e.tokenGetAccountNftInfos, o.uint32(434).fork()).ldelim(),
            null != e.tokenGetNftInfo &&
              Object.hasOwnProperty.call(e, 'tokenGetNftInfo') &&
              $root.proto.TokenGetNftInfoQuery.encode(e.tokenGetNftInfo, o.uint32(442).fork()).ldelim(),
            null != e.tokenGetNftInfos &&
              Object.hasOwnProperty.call(e, 'tokenGetNftInfos') &&
              $root.proto.TokenGetNftInfosQuery.encode(e.tokenGetNftInfos, o.uint32(450).fork()).ldelim(),
            null != e.networkGetExecutionTime &&
              Object.hasOwnProperty.call(e, 'networkGetExecutionTime') &&
              $root.proto.NetworkGetExecutionTimeQuery.encode(e.networkGetExecutionTime, o.uint32(458).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Query(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.getByKey = $root.proto.GetByKeyQuery.decode(e, e.uint32());
                break;
              case 2:
                i.getBySolidityID = $root.proto.GetBySolidityIDQuery.decode(e, e.uint32());
                break;
              case 3:
                i.contractCallLocal = $root.proto.ContractCallLocalQuery.decode(e, e.uint32());
                break;
              case 4:
                i.contractGetInfo = $root.proto.ContractGetInfoQuery.decode(e, e.uint32());
                break;
              case 5:
                i.contractGetBytecode = $root.proto.ContractGetBytecodeQuery.decode(e, e.uint32());
                break;
              case 6:
                i.ContractGetRecords = $root.proto.ContractGetRecordsQuery.decode(e, e.uint32());
                break;
              case 7:
                i.cryptogetAccountBalance = $root.proto.CryptoGetAccountBalanceQuery.decode(e, e.uint32());
                break;
              case 8:
                i.cryptoGetAccountRecords = $root.proto.CryptoGetAccountRecordsQuery.decode(e, e.uint32());
                break;
              case 9:
                i.cryptoGetInfo = $root.proto.CryptoGetInfoQuery.decode(e, e.uint32());
                break;
              case 10:
                i.cryptoGetLiveHash = $root.proto.CryptoGetLiveHashQuery.decode(e, e.uint32());
                break;
              case 11:
                i.cryptoGetProxyStakers = $root.proto.CryptoGetStakersQuery.decode(e, e.uint32());
                break;
              case 12:
                i.fileGetContents = $root.proto.FileGetContentsQuery.decode(e, e.uint32());
                break;
              case 13:
                i.fileGetInfo = $root.proto.FileGetInfoQuery.decode(e, e.uint32());
                break;
              case 14:
                i.transactionGetReceipt = $root.proto.TransactionGetReceiptQuery.decode(e, e.uint32());
                break;
              case 15:
                i.transactionGetRecord = $root.proto.TransactionGetRecordQuery.decode(e, e.uint32());
                break;
              case 16:
                i.transactionGetFastRecord = $root.proto.TransactionGetFastRecordQuery.decode(e, e.uint32());
                break;
              case 50:
                i.consensusGetTopicInfo = $root.proto.ConsensusGetTopicInfoQuery.decode(e, e.uint32());
                break;
              case 51:
                i.networkGetVersionInfo = $root.proto.NetworkGetVersionInfoQuery.decode(e, e.uint32());
                break;
              case 52:
                i.tokenGetInfo = $root.proto.TokenGetInfoQuery.decode(e, e.uint32());
                break;
              case 53:
                i.scheduleGetInfo = $root.proto.ScheduleGetInfoQuery.decode(e, e.uint32());
                break;
              case 54:
                i.tokenGetAccountNftInfos = $root.proto.TokenGetAccountNftInfosQuery.decode(e, e.uint32());
                break;
              case 55:
                i.tokenGetNftInfo = $root.proto.TokenGetNftInfoQuery.decode(e, e.uint32());
                break;
              case 56:
                i.tokenGetNftInfos = $root.proto.TokenGetNftInfosQuery.decode(e, e.uint32());
                break;
              case 57:
                i.networkGetExecutionTime = $root.proto.NetworkGetExecutionTimeQuery.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    GetByKeyQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.key = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.key &&
              Object.hasOwnProperty.call(e, 'key') &&
              $root.proto.Key.encode(e.key, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.GetByKeyQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.key = $root.proto.Key.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    EntityID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.accountID = null),
        (e.prototype.liveHash = null),
        (e.prototype.fileID = null),
        (e.prototype.contractID = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'entity', {
          get: $util.oneOfGetter((o = ['accountID', 'liveHash', 'fileID', 'contractID'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(10).fork()).ldelim(),
            null != e.liveHash &&
              Object.hasOwnProperty.call(e, 'liveHash') &&
              $root.proto.LiveHash.encode(e.liveHash, o.uint32(18).fork()).ldelim(),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(26).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(34).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.EntityID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.liveHash = $root.proto.LiveHash.decode(e, e.uint32());
                break;
              case 3:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 4:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    GetByKeyResponse: (function () {
      function e(e) {
        if (((this.entities = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.entities = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.entities && e.entities.length)
          )
            for (var t = 0; t < e.entities.length; ++t)
              $root.proto.EntityID.encode(e.entities[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.GetByKeyResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                (i.entities && i.entities.length) || (i.entities = []),
                  i.entities.push($root.proto.EntityID.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    GetBySolidityIDQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.solidityID = ''),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.solidityID && Object.hasOwnProperty.call(e, 'solidityID') && o.uint32(18).string(e.solidityID),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.GetBySolidityIDQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.solidityID = e.string();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    GetBySolidityIDResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.prototype.fileID = null),
        (e.prototype.contractID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(26).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(34).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.GetBySolidityIDResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 4:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractLoginfo: (function () {
      function e(e) {
        if (((this.topic = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.contractID = null),
        (e.prototype.bloom = $util.newBuffer([])),
        (e.prototype.topic = $util.emptyArray),
        (e.prototype.data = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(10).fork()).ldelim(),
            null != e.bloom && Object.hasOwnProperty.call(e, 'bloom') && o.uint32(18).bytes(e.bloom),
            null != e.topic && e.topic.length)
          )
            for (var t = 0; t < e.topic.length; ++t) o.uint32(26).bytes(e.topic[t]);
          return null != e.data && Object.hasOwnProperty.call(e, 'data') && o.uint32(34).bytes(e.data), o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractLoginfo(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 2:
                i.bloom = e.bytes();
                break;
              case 3:
                (i.topic && i.topic.length) || (i.topic = []), i.topic.push(e.bytes());
                break;
              case 4:
                i.data = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractFunctionResult: (function () {
      function e(e) {
        if (((this.logInfo = []), (this.createdContractIDs = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.contractID = null),
        (e.prototype.contractCallResult = $util.newBuffer([])),
        (e.prototype.errorMessage = ''),
        (e.prototype.bloom = $util.newBuffer([])),
        (e.prototype.gasUsed = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.logInfo = $util.emptyArray),
        (e.prototype.createdContractIDs = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(10).fork()).ldelim(),
            null != e.contractCallResult &&
              Object.hasOwnProperty.call(e, 'contractCallResult') &&
              o.uint32(18).bytes(e.contractCallResult),
            null != e.errorMessage &&
              Object.hasOwnProperty.call(e, 'errorMessage') &&
              o.uint32(26).string(e.errorMessage),
            null != e.bloom && Object.hasOwnProperty.call(e, 'bloom') && o.uint32(34).bytes(e.bloom),
            null != e.gasUsed && Object.hasOwnProperty.call(e, 'gasUsed') && o.uint32(40).uint64(e.gasUsed),
            null != e.logInfo && e.logInfo.length)
          )
            for (var t = 0; t < e.logInfo.length; ++t)
              $root.proto.ContractLoginfo.encode(e.logInfo[t], o.uint32(50).fork()).ldelim();
          if (null != e.createdContractIDs && e.createdContractIDs.length)
            for (var t = 0; t < e.createdContractIDs.length; ++t)
              $root.proto.ContractID.encode(e.createdContractIDs[t], o.uint32(58).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractFunctionResult(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 2:
                i.contractCallResult = e.bytes();
                break;
              case 3:
                i.errorMessage = e.string();
                break;
              case 4:
                i.bloom = e.bytes();
                break;
              case 5:
                i.gasUsed = e.uint64();
                break;
              case 6:
                (i.logInfo && i.logInfo.length) || (i.logInfo = []),
                  i.logInfo.push($root.proto.ContractLoginfo.decode(e, e.uint32()));
                break;
              case 7:
                (i.createdContractIDs && i.createdContractIDs.length) || (i.createdContractIDs = []),
                  i.createdContractIDs.push($root.proto.ContractID.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractCallLocalQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.contractID = null),
        (e.prototype.gas = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.functionParameters = $util.newBuffer([])),
        (e.prototype.maxResultSize = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(18).fork()).ldelim(),
            null != e.gas && Object.hasOwnProperty.call(e, 'gas') && o.uint32(24).int64(e.gas),
            null != e.functionParameters &&
              Object.hasOwnProperty.call(e, 'functionParameters') &&
              o.uint32(34).bytes(e.functionParameters),
            null != e.maxResultSize &&
              Object.hasOwnProperty.call(e, 'maxResultSize') &&
              o.uint32(40).int64(e.maxResultSize),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractCallLocalQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 3:
                i.gas = e.int64();
                break;
              case 4:
                i.functionParameters = e.bytes();
                break;
              case 5:
                i.maxResultSize = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractCallLocalResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.functionResult = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.functionResult &&
              Object.hasOwnProperty.call(e, 'functionResult') &&
              $root.proto.ContractFunctionResult.encode(e.functionResult, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractCallLocalResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.functionResult = $root.proto.ContractFunctionResult.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractGetInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.contractID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractGetInfoQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractGetInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.contractInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.contractInfo &&
              Object.hasOwnProperty.call(e, 'contractInfo') &&
              $root.proto.ContractGetInfoResponse.ContractInfo.encode(e.contractInfo, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractGetInfoResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.contractInfo = $root.proto.ContractGetInfoResponse.ContractInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        (e.ContractInfo = (function () {
          function e(e) {
            if (((this.tokenRelationships = []), e))
              for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.contractID = null),
            (e.prototype.accountID = null),
            (e.prototype.contractAccountID = ''),
            (e.prototype.adminKey = null),
            (e.prototype.expirationTime = null),
            (e.prototype.autoRenewPeriod = null),
            (e.prototype.storage = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
            (e.prototype.memo = ''),
            (e.prototype.balance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
            (e.prototype.deleted = !1),
            (e.prototype.tokenRelationships = $util.emptyArray),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              if (
                (o || (o = $Writer.create()),
                null != e.contractID &&
                  Object.hasOwnProperty.call(e, 'contractID') &&
                  $root.proto.ContractID.encode(e.contractID, o.uint32(10).fork()).ldelim(),
                null != e.accountID &&
                  Object.hasOwnProperty.call(e, 'accountID') &&
                  $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
                null != e.contractAccountID &&
                  Object.hasOwnProperty.call(e, 'contractAccountID') &&
                  o.uint32(26).string(e.contractAccountID),
                null != e.adminKey &&
                  Object.hasOwnProperty.call(e, 'adminKey') &&
                  $root.proto.Key.encode(e.adminKey, o.uint32(34).fork()).ldelim(),
                null != e.expirationTime &&
                  Object.hasOwnProperty.call(e, 'expirationTime') &&
                  $root.proto.Timestamp.encode(e.expirationTime, o.uint32(42).fork()).ldelim(),
                null != e.autoRenewPeriod &&
                  Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
                  $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(50).fork()).ldelim(),
                null != e.storage && Object.hasOwnProperty.call(e, 'storage') && o.uint32(56).int64(e.storage),
                null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(66).string(e.memo),
                null != e.balance && Object.hasOwnProperty.call(e, 'balance') && o.uint32(72).uint64(e.balance),
                null != e.deleted && Object.hasOwnProperty.call(e, 'deleted') && o.uint32(80).bool(e.deleted),
                null != e.tokenRelationships && e.tokenRelationships.length)
              )
                for (var t = 0; t < e.tokenRelationships.length; ++t)
                  $root.proto.TokenRelationship.encode(e.tokenRelationships[t], o.uint32(90).fork()).ldelim();
              return o;
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractGetInfoResponse.ContractInfo(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                    break;
                  case 2:
                    i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                    break;
                  case 3:
                    i.contractAccountID = e.string();
                    break;
                  case 4:
                    i.adminKey = $root.proto.Key.decode(e, e.uint32());
                    break;
                  case 5:
                    i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                    break;
                  case 6:
                    i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                    break;
                  case 7:
                    i.storage = e.int64();
                    break;
                  case 8:
                    i.memo = e.string();
                    break;
                  case 9:
                    i.balance = e.uint64();
                    break;
                  case 10:
                    i.deleted = e.bool();
                    break;
                  case 11:
                    (i.tokenRelationships && i.tokenRelationships.length) || (i.tokenRelationships = []),
                      i.tokenRelationships.push($root.proto.TokenRelationship.decode(e, e.uint32()));
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })()),
        e
      );
    })(),
    ContractGetBytecodeQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.contractID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractGetBytecodeQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractGetBytecodeResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.bytecode = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.bytecode && Object.hasOwnProperty.call(e, 'bytecode') && o.uint32(50).bytes(e.bytecode),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractGetBytecodeResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 6:
                i.bytecode = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractGetRecordsQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.contractID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractGetRecordsQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ContractGetRecordsResponse: (function () {
      function e(e) {
        if (((this.records = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.contractID = null),
        (e.prototype.records = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(18).fork()).ldelim(),
            null != e.records && e.records.length)
          )
            for (var t = 0; t < e.records.length; ++t)
              $root.proto.TransactionRecord.encode(e.records[t], o.uint32(26).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ContractGetRecordsResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 3:
                (i.records && i.records.length) || (i.records = []),
                  i.records.push($root.proto.TransactionRecord.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionRecord: (function () {
      function e(e) {
        if (((this.tokenTransferLists = []), (this.assessedCustomFees = []), (this.automaticTokenAssociations = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.receipt = null),
        (e.prototype.transactionHash = $util.newBuffer([])),
        (e.prototype.consensusTimestamp = null),
        (e.prototype.transactionID = null),
        (e.prototype.memo = ''),
        (e.prototype.transactionFee = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.contractCallResult = null),
        (e.prototype.contractCreateResult = null),
        (e.prototype.transferList = null),
        (e.prototype.tokenTransferLists = $util.emptyArray),
        (e.prototype.scheduleRef = null),
        (e.prototype.assessedCustomFees = $util.emptyArray),
        (e.prototype.automaticTokenAssociations = $util.emptyArray),
        (e.prototype.parentConsensusTimestamp = null),
        (e.prototype.alias = $util.newBuffer([]));
      let o;
      return (
        Object.defineProperty(e.prototype, 'body', {
          get: $util.oneOfGetter((o = ['contractCallResult', 'contractCreateResult'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.receipt &&
              Object.hasOwnProperty.call(e, 'receipt') &&
              $root.proto.TransactionReceipt.encode(e.receipt, o.uint32(10).fork()).ldelim(),
            null != e.transactionHash &&
              Object.hasOwnProperty.call(e, 'transactionHash') &&
              o.uint32(18).bytes(e.transactionHash),
            null != e.consensusTimestamp &&
              Object.hasOwnProperty.call(e, 'consensusTimestamp') &&
              $root.proto.Timestamp.encode(e.consensusTimestamp, o.uint32(26).fork()).ldelim(),
            null != e.transactionID &&
              Object.hasOwnProperty.call(e, 'transactionID') &&
              $root.proto.TransactionID.encode(e.transactionID, o.uint32(34).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(42).string(e.memo),
            null != e.transactionFee &&
              Object.hasOwnProperty.call(e, 'transactionFee') &&
              o.uint32(48).uint64(e.transactionFee),
            null != e.contractCallResult &&
              Object.hasOwnProperty.call(e, 'contractCallResult') &&
              $root.proto.ContractFunctionResult.encode(e.contractCallResult, o.uint32(58).fork()).ldelim(),
            null != e.contractCreateResult &&
              Object.hasOwnProperty.call(e, 'contractCreateResult') &&
              $root.proto.ContractFunctionResult.encode(e.contractCreateResult, o.uint32(66).fork()).ldelim(),
            null != e.transferList &&
              Object.hasOwnProperty.call(e, 'transferList') &&
              $root.proto.TransferList.encode(e.transferList, o.uint32(82).fork()).ldelim(),
            null != e.tokenTransferLists && e.tokenTransferLists.length)
          )
            for (var t = 0; t < e.tokenTransferLists.length; ++t)
              $root.proto.TokenTransferList.encode(e.tokenTransferLists[t], o.uint32(90).fork()).ldelim();
          if (
            (null != e.scheduleRef &&
              Object.hasOwnProperty.call(e, 'scheduleRef') &&
              $root.proto.ScheduleID.encode(e.scheduleRef, o.uint32(98).fork()).ldelim(),
            null != e.assessedCustomFees && e.assessedCustomFees.length)
          )
            for (var t = 0; t < e.assessedCustomFees.length; ++t)
              $root.proto.AssessedCustomFee.encode(e.assessedCustomFees[t], o.uint32(106).fork()).ldelim();
          if (null != e.automaticTokenAssociations && e.automaticTokenAssociations.length)
            for (var t = 0; t < e.automaticTokenAssociations.length; ++t)
              $root.proto.TokenAssociation.encode(e.automaticTokenAssociations[t], o.uint32(114).fork()).ldelim();
          return (
            null != e.parentConsensusTimestamp &&
              Object.hasOwnProperty.call(e, 'parentConsensusTimestamp') &&
              $root.proto.Timestamp.encode(e.parentConsensusTimestamp, o.uint32(122).fork()).ldelim(),
            null != e.alias && Object.hasOwnProperty.call(e, 'alias') && o.uint32(130).bytes(e.alias),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionRecord(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.receipt = $root.proto.TransactionReceipt.decode(e, e.uint32());
                break;
              case 2:
                i.transactionHash = e.bytes();
                break;
              case 3:
                i.consensusTimestamp = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 4:
                i.transactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              case 5:
                i.memo = e.string();
                break;
              case 6:
                i.transactionFee = e.uint64();
                break;
              case 7:
                i.contractCallResult = $root.proto.ContractFunctionResult.decode(e, e.uint32());
                break;
              case 8:
                i.contractCreateResult = $root.proto.ContractFunctionResult.decode(e, e.uint32());
                break;
              case 10:
                i.transferList = $root.proto.TransferList.decode(e, e.uint32());
                break;
              case 11:
                (i.tokenTransferLists && i.tokenTransferLists.length) || (i.tokenTransferLists = []),
                  i.tokenTransferLists.push($root.proto.TokenTransferList.decode(e, e.uint32()));
                break;
              case 12:
                i.scheduleRef = $root.proto.ScheduleID.decode(e, e.uint32());
                break;
              case 13:
                (i.assessedCustomFees && i.assessedCustomFees.length) || (i.assessedCustomFees = []),
                  i.assessedCustomFees.push($root.proto.AssessedCustomFee.decode(e, e.uint32()));
                break;
              case 14:
                (i.automaticTokenAssociations && i.automaticTokenAssociations.length) ||
                  (i.automaticTokenAssociations = []),
                  i.automaticTokenAssociations.push($root.proto.TokenAssociation.decode(e, e.uint32()));
                break;
              case 15:
                i.parentConsensusTimestamp = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 16:
                i.alias = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionReceipt: (function () {
      function e(e) {
        if (((this.serialNumbers = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.status = 0),
        (e.prototype.accountID = null),
        (e.prototype.fileID = null),
        (e.prototype.contractID = null),
        (e.prototype.exchangeRate = null),
        (e.prototype.topicID = null),
        (e.prototype.topicSequenceNumber = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.topicRunningHash = $util.newBuffer([])),
        (e.prototype.topicRunningHashVersion = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.tokenID = null),
        (e.prototype.newTotalSupply = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.scheduleID = null),
        (e.prototype.scheduledTransactionID = null),
        (e.prototype.serialNumbers = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.status && Object.hasOwnProperty.call(e, 'status') && o.uint32(8).int32(e.status),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(26).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(34).fork()).ldelim(),
            null != e.exchangeRate &&
              Object.hasOwnProperty.call(e, 'exchangeRate') &&
              $root.proto.ExchangeRateSet.encode(e.exchangeRate, o.uint32(42).fork()).ldelim(),
            null != e.topicID &&
              Object.hasOwnProperty.call(e, 'topicID') &&
              $root.proto.TopicID.encode(e.topicID, o.uint32(50).fork()).ldelim(),
            null != e.topicSequenceNumber &&
              Object.hasOwnProperty.call(e, 'topicSequenceNumber') &&
              o.uint32(56).uint64(e.topicSequenceNumber),
            null != e.topicRunningHash &&
              Object.hasOwnProperty.call(e, 'topicRunningHash') &&
              o.uint32(66).bytes(e.topicRunningHash),
            null != e.topicRunningHashVersion &&
              Object.hasOwnProperty.call(e, 'topicRunningHashVersion') &&
              o.uint32(72).uint64(e.topicRunningHashVersion),
            null != e.tokenID &&
              Object.hasOwnProperty.call(e, 'tokenID') &&
              $root.proto.TokenID.encode(e.tokenID, o.uint32(82).fork()).ldelim(),
            null != e.newTotalSupply &&
              Object.hasOwnProperty.call(e, 'newTotalSupply') &&
              o.uint32(88).uint64(e.newTotalSupply),
            null != e.scheduleID &&
              Object.hasOwnProperty.call(e, 'scheduleID') &&
              $root.proto.ScheduleID.encode(e.scheduleID, o.uint32(98).fork()).ldelim(),
            null != e.scheduledTransactionID &&
              Object.hasOwnProperty.call(e, 'scheduledTransactionID') &&
              $root.proto.TransactionID.encode(e.scheduledTransactionID, o.uint32(106).fork()).ldelim(),
            null != e.serialNumbers && e.serialNumbers.length)
          ) {
            o.uint32(114).fork();
            for (var t = 0; t < e.serialNumbers.length; ++t) o.int64(e.serialNumbers[t]);
            o.ldelim();
          }
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionReceipt(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.status = e.int32();
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              case 4:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              case 5:
                i.exchangeRate = $root.proto.ExchangeRateSet.decode(e, e.uint32());
                break;
              case 6:
                i.topicID = $root.proto.TopicID.decode(e, e.uint32());
                break;
              case 7:
                i.topicSequenceNumber = e.uint64();
                break;
              case 8:
                i.topicRunningHash = e.bytes();
                break;
              case 9:
                i.topicRunningHashVersion = e.uint64();
                break;
              case 10:
                i.tokenID = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 11:
                i.newTotalSupply = e.uint64();
                break;
              case 12:
                i.scheduleID = $root.proto.ScheduleID.decode(e, e.uint32());
                break;
              case 13:
                i.scheduledTransactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              case 14:
                if (((i.serialNumbers && i.serialNumbers.length) || (i.serialNumbers = []), 2 == (7 & d)))
                  for (var a = e.uint32() + e.pos; e.pos < a; ) i.serialNumbers.push(e.int64());
                else i.serialNumbers.push(e.int64());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ExchangeRate: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.hbarEquiv = 0),
        (e.prototype.centEquiv = 0),
        (e.prototype.expirationTime = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.hbarEquiv && Object.hasOwnProperty.call(e, 'hbarEquiv') && o.uint32(8).int32(e.hbarEquiv),
            null != e.centEquiv && Object.hasOwnProperty.call(e, 'centEquiv') && o.uint32(16).int32(e.centEquiv),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.TimestampSeconds.encode(e.expirationTime, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ExchangeRate(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.hbarEquiv = e.int32();
                break;
              case 2:
                i.centEquiv = e.int32();
                break;
              case 3:
                i.expirationTime = $root.proto.TimestampSeconds.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ExchangeRateSet: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.currentRate = null),
        (e.prototype.nextRate = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.currentRate &&
              Object.hasOwnProperty.call(e, 'currentRate') &&
              $root.proto.ExchangeRate.encode(e.currentRate, o.uint32(10).fork()).ldelim(),
            null != e.nextRate &&
              Object.hasOwnProperty.call(e, 'nextRate') &&
              $root.proto.ExchangeRate.encode(e.nextRate, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ExchangeRateSet(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.currentRate = $root.proto.ExchangeRate.decode(e, e.uint32());
                break;
              case 2:
                i.nextRate = $root.proto.ExchangeRate.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetAccountBalanceQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.header = null), (e.prototype.accountID = null), (e.prototype.contractID = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'balanceSource', {
          get: $util.oneOfGetter((o = ['accountID', 'contractID'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.contractID &&
              Object.hasOwnProperty.call(e, 'contractID') &&
              $root.proto.ContractID.encode(e.contractID, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetAccountBalanceQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.contractID = $root.proto.ContractID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetAccountBalanceResponse: (function () {
      function e(e) {
        if (((this.tokenBalances = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.prototype.balance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.tokenBalances = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.balance && Object.hasOwnProperty.call(e, 'balance') && o.uint32(24).uint64(e.balance),
            null != e.tokenBalances && e.tokenBalances.length)
          )
            for (var t = 0; t < e.tokenBalances.length; ++t)
              $root.proto.TokenBalance.encode(e.tokenBalances[t], o.uint32(34).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetAccountBalanceResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.balance = e.uint64();
                break;
              case 4:
                (i.tokenBalances && i.tokenBalances.length) || (i.tokenBalances = []),
                  i.tokenBalances.push($root.proto.TokenBalance.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetAccountRecordsQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetAccountRecordsQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetAccountRecordsResponse: (function () {
      function e(e) {
        if (((this.records = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.prototype.records = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.records && e.records.length)
          )
            for (var t = 0; t < e.records.length; ++t)
              $root.proto.TransactionRecord.encode(e.records[t], o.uint32(26).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetAccountRecordsResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                (i.records && i.records.length) || (i.records = []),
                  i.records.push($root.proto.TransactionRecord.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetInfoQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountInfo &&
              Object.hasOwnProperty.call(e, 'accountInfo') &&
              $root.proto.CryptoGetInfoResponse.AccountInfo.encode(e.accountInfo, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetInfoResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountInfo = $root.proto.CryptoGetInfoResponse.AccountInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        (e.AccountInfo = (function () {
          function e(e) {
            if (((this.liveHashes = []), (this.tokenRelationships = []), e))
              for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.accountID = null),
            (e.prototype.contractAccountID = ''),
            (e.prototype.deleted = !1),
            (e.prototype.proxyAccountID = null),
            (e.prototype.proxyReceived = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
            (e.prototype.key = null),
            (e.prototype.balance = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
            (e.prototype.generateSendRecordThreshold = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
            (e.prototype.generateReceiveRecordThreshold = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
            (e.prototype.receiverSigRequired = !1),
            (e.prototype.expirationTime = null),
            (e.prototype.autoRenewPeriod = null),
            (e.prototype.liveHashes = $util.emptyArray),
            (e.prototype.tokenRelationships = $util.emptyArray),
            (e.prototype.memo = ''),
            (e.prototype.ownedNfts = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
            (e.prototype.maxAutomaticTokenAssociations = 0),
            (e.prototype.alias = $util.newBuffer([])),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              if (
                (o || (o = $Writer.create()),
                null != e.accountID &&
                  Object.hasOwnProperty.call(e, 'accountID') &&
                  $root.proto.AccountID.encode(e.accountID, o.uint32(10).fork()).ldelim(),
                null != e.contractAccountID &&
                  Object.hasOwnProperty.call(e, 'contractAccountID') &&
                  o.uint32(18).string(e.contractAccountID),
                null != e.deleted && Object.hasOwnProperty.call(e, 'deleted') && o.uint32(24).bool(e.deleted),
                null != e.proxyAccountID &&
                  Object.hasOwnProperty.call(e, 'proxyAccountID') &&
                  $root.proto.AccountID.encode(e.proxyAccountID, o.uint32(34).fork()).ldelim(),
                null != e.proxyReceived &&
                  Object.hasOwnProperty.call(e, 'proxyReceived') &&
                  o.uint32(48).int64(e.proxyReceived),
                null != e.key &&
                  Object.hasOwnProperty.call(e, 'key') &&
                  $root.proto.Key.encode(e.key, o.uint32(58).fork()).ldelim(),
                null != e.balance && Object.hasOwnProperty.call(e, 'balance') && o.uint32(64).uint64(e.balance),
                null != e.generateSendRecordThreshold &&
                  Object.hasOwnProperty.call(e, 'generateSendRecordThreshold') &&
                  o.uint32(72).uint64(e.generateSendRecordThreshold),
                null != e.generateReceiveRecordThreshold &&
                  Object.hasOwnProperty.call(e, 'generateReceiveRecordThreshold') &&
                  o.uint32(80).uint64(e.generateReceiveRecordThreshold),
                null != e.receiverSigRequired &&
                  Object.hasOwnProperty.call(e, 'receiverSigRequired') &&
                  o.uint32(88).bool(e.receiverSigRequired),
                null != e.expirationTime &&
                  Object.hasOwnProperty.call(e, 'expirationTime') &&
                  $root.proto.Timestamp.encode(e.expirationTime, o.uint32(98).fork()).ldelim(),
                null != e.autoRenewPeriod &&
                  Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
                  $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(106).fork()).ldelim(),
                null != e.liveHashes && e.liveHashes.length)
              )
                for (var t = 0; t < e.liveHashes.length; ++t)
                  $root.proto.LiveHash.encode(e.liveHashes[t], o.uint32(114).fork()).ldelim();
              if (null != e.tokenRelationships && e.tokenRelationships.length)
                for (var t = 0; t < e.tokenRelationships.length; ++t)
                  $root.proto.TokenRelationship.encode(e.tokenRelationships[t], o.uint32(122).fork()).ldelim();
              return (
                null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(130).string(e.memo),
                null != e.ownedNfts && Object.hasOwnProperty.call(e, 'ownedNfts') && o.uint32(136).int64(e.ownedNfts),
                null != e.maxAutomaticTokenAssociations &&
                  Object.hasOwnProperty.call(e, 'maxAutomaticTokenAssociations') &&
                  o.uint32(144).int32(e.maxAutomaticTokenAssociations),
                null != e.alias && Object.hasOwnProperty.call(e, 'alias') && o.uint32(154).bytes(e.alias),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetInfoResponse.AccountInfo(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                    break;
                  case 2:
                    i.contractAccountID = e.string();
                    break;
                  case 3:
                    i.deleted = e.bool();
                    break;
                  case 4:
                    i.proxyAccountID = $root.proto.AccountID.decode(e, e.uint32());
                    break;
                  case 6:
                    i.proxyReceived = e.int64();
                    break;
                  case 7:
                    i.key = $root.proto.Key.decode(e, e.uint32());
                    break;
                  case 8:
                    i.balance = e.uint64();
                    break;
                  case 9:
                    i.generateSendRecordThreshold = e.uint64();
                    break;
                  case 10:
                    i.generateReceiveRecordThreshold = e.uint64();
                    break;
                  case 11:
                    i.receiverSigRequired = e.bool();
                    break;
                  case 12:
                    i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                    break;
                  case 13:
                    i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                    break;
                  case 14:
                    (i.liveHashes && i.liveHashes.length) || (i.liveHashes = []),
                      i.liveHashes.push($root.proto.LiveHash.decode(e, e.uint32()));
                    break;
                  case 15:
                    (i.tokenRelationships && i.tokenRelationships.length) || (i.tokenRelationships = []),
                      i.tokenRelationships.push($root.proto.TokenRelationship.decode(e, e.uint32()));
                    break;
                  case 16:
                    i.memo = e.string();
                    break;
                  case 17:
                    i.ownedNfts = e.int64();
                    break;
                  case 18:
                    i.maxAutomaticTokenAssociations = e.int32();
                    break;
                  case 19:
                    i.alias = e.bytes();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })()),
        e
      );
    })(),
    CryptoGetLiveHashQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.prototype.hash = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.hash && Object.hasOwnProperty.call(e, 'hash') && o.uint32(26).bytes(e.hash),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetLiveHashQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.hash = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetLiveHashResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.liveHash = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.liveHash &&
              Object.hasOwnProperty.call(e, 'liveHash') &&
              $root.proto.LiveHash.encode(e.liveHash, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetLiveHashResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.liveHash = $root.proto.LiveHash.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetStakersQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetStakersQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ProxyStaker: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.accountID = null),
        (e.prototype.amount = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(10).fork()).ldelim(),
            null != e.amount && Object.hasOwnProperty.call(e, 'amount') && o.uint32(16).int64(e.amount),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ProxyStaker(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                i.amount = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    AllProxyStakers: (function () {
      function e(e) {
        if (((this.proxyStaker = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.accountID = null),
        (e.prototype.proxyStaker = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(10).fork()).ldelim(),
            null != e.proxyStaker && e.proxyStaker.length)
          )
            for (var t = 0; t < e.proxyStaker.length; ++t)
              $root.proto.ProxyStaker.encode(e.proxyStaker[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.AllProxyStakers(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 2:
                (i.proxyStaker && i.proxyStaker.length) || (i.proxyStaker = []),
                  i.proxyStaker.push($root.proto.ProxyStaker.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoGetStakersResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.stakers = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.stakers &&
              Object.hasOwnProperty.call(e, 'stakers') &&
              $root.proto.AllProxyStakers.encode(e.stakers, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.CryptoGetStakersResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 3:
                i.stakers = $root.proto.AllProxyStakers.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileGetContentsQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.fileID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileGetContentsQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileGetContentsResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.fileContents = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.fileContents &&
              Object.hasOwnProperty.call(e, 'fileContents') &&
              $root.proto.FileGetContentsResponse.FileContents.encode(e.fileContents, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileGetContentsResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.fileContents = $root.proto.FileGetContentsResponse.FileContents.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        (e.FileContents = (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.fileID = null),
            (e.prototype.contents = $util.newBuffer([])),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.fileID &&
                  Object.hasOwnProperty.call(e, 'fileID') &&
                  $root.proto.FileID.encode(e.fileID, o.uint32(10).fork()).ldelim(),
                null != e.contents && Object.hasOwnProperty.call(e, 'contents') && o.uint32(18).bytes(e.contents),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileGetContentsResponse.FileContents(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.fileID = $root.proto.FileID.decode(e, e.uint32());
                    break;
                  case 2:
                    i.contents = e.bytes();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })()),
        e
      );
    })(),
    FileGetInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.fileID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.fileID &&
              Object.hasOwnProperty.call(e, 'fileID') &&
              $root.proto.FileID.encode(e.fileID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileGetInfoQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.fileID = $root.proto.FileID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FileGetInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.fileInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.fileInfo &&
              Object.hasOwnProperty.call(e, 'fileInfo') &&
              $root.proto.FileGetInfoResponse.FileInfo.encode(e.fileInfo, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileGetInfoResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.fileInfo = $root.proto.FileGetInfoResponse.FileInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        (e.FileInfo = (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.fileID = null),
            (e.prototype.size = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
            (e.prototype.expirationTime = null),
            (e.prototype.deleted = !1),
            (e.prototype.keys = null),
            (e.prototype.memo = ''),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.fileID &&
                  Object.hasOwnProperty.call(e, 'fileID') &&
                  $root.proto.FileID.encode(e.fileID, o.uint32(10).fork()).ldelim(),
                null != e.size && Object.hasOwnProperty.call(e, 'size') && o.uint32(16).int64(e.size),
                null != e.expirationTime &&
                  Object.hasOwnProperty.call(e, 'expirationTime') &&
                  $root.proto.Timestamp.encode(e.expirationTime, o.uint32(26).fork()).ldelim(),
                null != e.deleted && Object.hasOwnProperty.call(e, 'deleted') && o.uint32(32).bool(e.deleted),
                null != e.keys &&
                  Object.hasOwnProperty.call(e, 'keys') &&
                  $root.proto.KeyList.encode(e.keys, o.uint32(42).fork()).ldelim(),
                null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(50).string(e.memo),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FileGetInfoResponse.FileInfo(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.fileID = $root.proto.FileID.decode(e, e.uint32());
                    break;
                  case 2:
                    i.size = e.int64();
                    break;
                  case 3:
                    i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                    break;
                  case 4:
                    i.deleted = e.bool();
                    break;
                  case 5:
                    i.keys = $root.proto.KeyList.decode(e, e.uint32());
                    break;
                  case 6:
                    i.memo = e.string();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })()),
        e
      );
    })(),
    TransactionGetReceiptQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.transactionID = null),
        (e.prototype.includeDuplicates = !1),
        (e.prototype.includeChildReceipts = !1),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.transactionID &&
              Object.hasOwnProperty.call(e, 'transactionID') &&
              $root.proto.TransactionID.encode(e.transactionID, o.uint32(18).fork()).ldelim(),
            null != e.includeDuplicates &&
              Object.hasOwnProperty.call(e, 'includeDuplicates') &&
              o.uint32(24).bool(e.includeDuplicates),
            null != e.includeChildReceipts &&
              Object.hasOwnProperty.call(e, 'includeChildReceipts') &&
              o.uint32(32).bool(e.includeChildReceipts),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionGetReceiptQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.transactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              case 3:
                i.includeDuplicates = e.bool();
                break;
              case 4:
                i.includeChildReceipts = e.bool();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionGetReceiptResponse: (function () {
      function e(e) {
        if (((this.duplicateTransactionReceipts = []), (this.childTransactionReceipts = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.receipt = null),
        (e.prototype.duplicateTransactionReceipts = $util.emptyArray),
        (e.prototype.childTransactionReceipts = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.receipt &&
              Object.hasOwnProperty.call(e, 'receipt') &&
              $root.proto.TransactionReceipt.encode(e.receipt, o.uint32(18).fork()).ldelim(),
            null != e.duplicateTransactionReceipts && e.duplicateTransactionReceipts.length)
          )
            for (var t = 0; t < e.duplicateTransactionReceipts.length; ++t)
              $root.proto.TransactionReceipt.encode(e.duplicateTransactionReceipts[t], o.uint32(34).fork()).ldelim();
          if (null != e.childTransactionReceipts && e.childTransactionReceipts.length)
            for (var t = 0; t < e.childTransactionReceipts.length; ++t)
              $root.proto.TransactionReceipt.encode(e.childTransactionReceipts[t], o.uint32(42).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionGetReceiptResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.receipt = $root.proto.TransactionReceipt.decode(e, e.uint32());
                break;
              case 4:
                (i.duplicateTransactionReceipts && i.duplicateTransactionReceipts.length) ||
                  (i.duplicateTransactionReceipts = []),
                  i.duplicateTransactionReceipts.push($root.proto.TransactionReceipt.decode(e, e.uint32()));
                break;
              case 5:
                (i.childTransactionReceipts && i.childTransactionReceipts.length) || (i.childTransactionReceipts = []),
                  i.childTransactionReceipts.push($root.proto.TransactionReceipt.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionGetRecordQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.transactionID = null),
        (e.prototype.includeDuplicates = !1),
        (e.prototype.includeChildRecords = !1),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.transactionID &&
              Object.hasOwnProperty.call(e, 'transactionID') &&
              $root.proto.TransactionID.encode(e.transactionID, o.uint32(18).fork()).ldelim(),
            null != e.includeDuplicates &&
              Object.hasOwnProperty.call(e, 'includeDuplicates') &&
              o.uint32(24).bool(e.includeDuplicates),
            null != e.includeChildRecords &&
              Object.hasOwnProperty.call(e, 'includeChildRecords') &&
              o.uint32(32).bool(e.includeChildRecords),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionGetRecordQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.transactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              case 3:
                i.includeDuplicates = e.bool();
                break;
              case 4:
                i.includeChildRecords = e.bool();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionGetRecordResponse: (function () {
      function e(e) {
        if (((this.duplicateTransactionRecords = []), (this.childTransactionRecords = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.transactionRecord = null),
        (e.prototype.duplicateTransactionRecords = $util.emptyArray),
        (e.prototype.childTransactionRecords = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.transactionRecord &&
              Object.hasOwnProperty.call(e, 'transactionRecord') &&
              $root.proto.TransactionRecord.encode(e.transactionRecord, o.uint32(26).fork()).ldelim(),
            null != e.duplicateTransactionRecords && e.duplicateTransactionRecords.length)
          )
            for (var t = 0; t < e.duplicateTransactionRecords.length; ++t)
              $root.proto.TransactionRecord.encode(e.duplicateTransactionRecords[t], o.uint32(34).fork()).ldelim();
          if (null != e.childTransactionRecords && e.childTransactionRecords.length)
            for (var t = 0; t < e.childTransactionRecords.length; ++t)
              $root.proto.TransactionRecord.encode(e.childTransactionRecords[t], o.uint32(42).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionGetRecordResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 3:
                i.transactionRecord = $root.proto.TransactionRecord.decode(e, e.uint32());
                break;
              case 4:
                (i.duplicateTransactionRecords && i.duplicateTransactionRecords.length) ||
                  (i.duplicateTransactionRecords = []),
                  i.duplicateTransactionRecords.push($root.proto.TransactionRecord.decode(e, e.uint32()));
                break;
              case 5:
                (i.childTransactionRecords && i.childTransactionRecords.length) || (i.childTransactionRecords = []),
                  i.childTransactionRecords.push($root.proto.TransactionRecord.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionGetFastRecordQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.transactionID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.transactionID &&
              Object.hasOwnProperty.call(e, 'transactionID') &&
              $root.proto.TransactionID.encode(e.transactionID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionGetFastRecordQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.transactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionGetFastRecordResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.transactionRecord = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.transactionRecord &&
              Object.hasOwnProperty.call(e, 'transactionRecord') &&
              $root.proto.TransactionRecord.encode(e.transactionRecord, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionGetFastRecordResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.transactionRecord = $root.proto.TransactionRecord.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NetworkGetVersionInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NetworkGetVersionInfoQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NetworkGetVersionInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.hapiProtoVersion = null),
        (e.prototype.hederaServicesVersion = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.hapiProtoVersion &&
              Object.hasOwnProperty.call(e, 'hapiProtoVersion') &&
              $root.proto.SemanticVersion.encode(e.hapiProtoVersion, o.uint32(18).fork()).ldelim(),
            null != e.hederaServicesVersion &&
              Object.hasOwnProperty.call(e, 'hederaServicesVersion') &&
              $root.proto.SemanticVersion.encode(e.hederaServicesVersion, o.uint32(26).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NetworkGetVersionInfoResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.hapiProtoVersion = $root.proto.SemanticVersion.decode(e, e.uint32());
                break;
              case 3:
                i.hederaServicesVersion = $root.proto.SemanticVersion.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NetworkGetExecutionTimeQuery: (function () {
      function e(e) {
        if (((this.transactionIds = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.transactionIds = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.transactionIds && e.transactionIds.length)
          )
            for (var t = 0; t < e.transactionIds.length; ++t)
              $root.proto.TransactionID.encode(e.transactionIds[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NetworkGetExecutionTimeQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                (i.transactionIds && i.transactionIds.length) || (i.transactionIds = []),
                  i.transactionIds.push($root.proto.TransactionID.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NetworkGetExecutionTimeResponse: (function () {
      function e(e) {
        if (((this.executionTimes = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.executionTimes = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.executionTimes && e.executionTimes.length)
          ) {
            o.uint32(18).fork();
            for (var t = 0; t < e.executionTimes.length; ++t) o.uint64(e.executionTimes[t]);
            o.ldelim();
          }
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NetworkGetExecutionTimeResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                if (((i.executionTimes && i.executionTimes.length) || (i.executionTimes = []), 2 == (7 & d)))
                  for (var a = e.uint32() + e.pos; e.pos < a; ) i.executionTimes.push(e.uint64());
                else i.executionTimes.push(e.uint64());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.token = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.token &&
              Object.hasOwnProperty.call(e, 'token') &&
              $root.proto.TokenID.encode(e.token, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetInfoQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.token = $root.proto.TokenID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenInfo: (function () {
      function e(e) {
        if (((this.customFees = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenId = null),
        (e.prototype.name = ''),
        (e.prototype.symbol = ''),
        (e.prototype.decimals = 0),
        (e.prototype.totalSupply = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.treasury = null),
        (e.prototype.adminKey = null),
        (e.prototype.kycKey = null),
        (e.prototype.freezeKey = null),
        (e.prototype.wipeKey = null),
        (e.prototype.supplyKey = null),
        (e.prototype.defaultFreezeStatus = 0),
        (e.prototype.defaultKycStatus = 0),
        (e.prototype.deleted = !1),
        (e.prototype.autoRenewAccount = null),
        (e.prototype.autoRenewPeriod = null),
        (e.prototype.expiry = null),
        (e.prototype.memo = ''),
        (e.prototype.tokenType = 0),
        (e.prototype.supplyType = 0),
        (e.prototype.maxSupply = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.feeScheduleKey = null),
        (e.prototype.customFees = $util.emptyArray),
        (e.prototype.pauseKey = null),
        (e.prototype.pauseStatus = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.tokenId &&
              Object.hasOwnProperty.call(e, 'tokenId') &&
              $root.proto.TokenID.encode(e.tokenId, o.uint32(10).fork()).ldelim(),
            null != e.name && Object.hasOwnProperty.call(e, 'name') && o.uint32(18).string(e.name),
            null != e.symbol && Object.hasOwnProperty.call(e, 'symbol') && o.uint32(26).string(e.symbol),
            null != e.decimals && Object.hasOwnProperty.call(e, 'decimals') && o.uint32(32).uint32(e.decimals),
            null != e.totalSupply && Object.hasOwnProperty.call(e, 'totalSupply') && o.uint32(40).uint64(e.totalSupply),
            null != e.treasury &&
              Object.hasOwnProperty.call(e, 'treasury') &&
              $root.proto.AccountID.encode(e.treasury, o.uint32(50).fork()).ldelim(),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(58).fork()).ldelim(),
            null != e.kycKey &&
              Object.hasOwnProperty.call(e, 'kycKey') &&
              $root.proto.Key.encode(e.kycKey, o.uint32(66).fork()).ldelim(),
            null != e.freezeKey &&
              Object.hasOwnProperty.call(e, 'freezeKey') &&
              $root.proto.Key.encode(e.freezeKey, o.uint32(74).fork()).ldelim(),
            null != e.wipeKey &&
              Object.hasOwnProperty.call(e, 'wipeKey') &&
              $root.proto.Key.encode(e.wipeKey, o.uint32(82).fork()).ldelim(),
            null != e.supplyKey &&
              Object.hasOwnProperty.call(e, 'supplyKey') &&
              $root.proto.Key.encode(e.supplyKey, o.uint32(90).fork()).ldelim(),
            null != e.defaultFreezeStatus &&
              Object.hasOwnProperty.call(e, 'defaultFreezeStatus') &&
              o.uint32(96).int32(e.defaultFreezeStatus),
            null != e.defaultKycStatus &&
              Object.hasOwnProperty.call(e, 'defaultKycStatus') &&
              o.uint32(104).int32(e.defaultKycStatus),
            null != e.deleted && Object.hasOwnProperty.call(e, 'deleted') && o.uint32(112).bool(e.deleted),
            null != e.autoRenewAccount &&
              Object.hasOwnProperty.call(e, 'autoRenewAccount') &&
              $root.proto.AccountID.encode(e.autoRenewAccount, o.uint32(122).fork()).ldelim(),
            null != e.autoRenewPeriod &&
              Object.hasOwnProperty.call(e, 'autoRenewPeriod') &&
              $root.proto.Duration.encode(e.autoRenewPeriod, o.uint32(130).fork()).ldelim(),
            null != e.expiry &&
              Object.hasOwnProperty.call(e, 'expiry') &&
              $root.proto.Timestamp.encode(e.expiry, o.uint32(138).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(146).string(e.memo),
            null != e.tokenType && Object.hasOwnProperty.call(e, 'tokenType') && o.uint32(152).int32(e.tokenType),
            null != e.supplyType && Object.hasOwnProperty.call(e, 'supplyType') && o.uint32(160).int32(e.supplyType),
            null != e.maxSupply && Object.hasOwnProperty.call(e, 'maxSupply') && o.uint32(168).int64(e.maxSupply),
            null != e.feeScheduleKey &&
              Object.hasOwnProperty.call(e, 'feeScheduleKey') &&
              $root.proto.Key.encode(e.feeScheduleKey, o.uint32(178).fork()).ldelim(),
            null != e.customFees && e.customFees.length)
          )
            for (var t = 0; t < e.customFees.length; ++t)
              $root.proto.CustomFee.encode(e.customFees[t], o.uint32(186).fork()).ldelim();
          return (
            null != e.pauseKey &&
              Object.hasOwnProperty.call(e, 'pauseKey') &&
              $root.proto.Key.encode(e.pauseKey, o.uint32(194).fork()).ldelim(),
            null != e.pauseStatus && Object.hasOwnProperty.call(e, 'pauseStatus') && o.uint32(200).int32(e.pauseStatus),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenInfo(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.tokenId = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.name = e.string();
                break;
              case 3:
                i.symbol = e.string();
                break;
              case 4:
                i.decimals = e.uint32();
                break;
              case 5:
                i.totalSupply = e.uint64();
                break;
              case 6:
                i.treasury = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 7:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 8:
                i.kycKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 9:
                i.freezeKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 10:
                i.wipeKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 11:
                i.supplyKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 12:
                i.defaultFreezeStatus = e.int32();
                break;
              case 13:
                i.defaultKycStatus = e.int32();
                break;
              case 14:
                i.deleted = e.bool();
                break;
              case 15:
                i.autoRenewAccount = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 16:
                i.autoRenewPeriod = $root.proto.Duration.decode(e, e.uint32());
                break;
              case 17:
                i.expiry = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 18:
                i.memo = e.string();
                break;
              case 19:
                i.tokenType = e.int32();
                break;
              case 20:
                i.supplyType = e.int32();
                break;
              case 21:
                i.maxSupply = e.int64();
                break;
              case 22:
                i.feeScheduleKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 23:
                (i.customFees && i.customFees.length) || (i.customFees = []),
                  i.customFees.push($root.proto.CustomFee.decode(e, e.uint32()));
                break;
              case 24:
                i.pauseKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 25:
                i.pauseStatus = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.tokenInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.tokenInfo &&
              Object.hasOwnProperty.call(e, 'tokenInfo') &&
              $root.proto.TokenInfo.encode(e.tokenInfo, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetInfoResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.tokenInfo = $root.proto.TokenInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ScheduleGetInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.scheduleID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.scheduleID &&
              Object.hasOwnProperty.call(e, 'scheduleID') &&
              $root.proto.ScheduleID.encode(e.scheduleID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ScheduleGetInfoQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.scheduleID = $root.proto.ScheduleID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ScheduleInfo: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.scheduleID = null),
        (e.prototype.deletionTime = null),
        (e.prototype.executionTime = null),
        (e.prototype.expirationTime = null),
        (e.prototype.scheduledTransactionBody = null),
        (e.prototype.memo = ''),
        (e.prototype.adminKey = null),
        (e.prototype.signers = null),
        (e.prototype.creatorAccountID = null),
        (e.prototype.payerAccountID = null),
        (e.prototype.scheduledTransactionID = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'data', {
          get: $util.oneOfGetter((o = ['deletionTime', 'executionTime'])),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.scheduleID &&
              Object.hasOwnProperty.call(e, 'scheduleID') &&
              $root.proto.ScheduleID.encode(e.scheduleID, o.uint32(10).fork()).ldelim(),
            null != e.deletionTime &&
              Object.hasOwnProperty.call(e, 'deletionTime') &&
              $root.proto.Timestamp.encode(e.deletionTime, o.uint32(18).fork()).ldelim(),
            null != e.executionTime &&
              Object.hasOwnProperty.call(e, 'executionTime') &&
              $root.proto.Timestamp.encode(e.executionTime, o.uint32(26).fork()).ldelim(),
            null != e.expirationTime &&
              Object.hasOwnProperty.call(e, 'expirationTime') &&
              $root.proto.Timestamp.encode(e.expirationTime, o.uint32(34).fork()).ldelim(),
            null != e.scheduledTransactionBody &&
              Object.hasOwnProperty.call(e, 'scheduledTransactionBody') &&
              $root.proto.SchedulableTransactionBody.encode(e.scheduledTransactionBody, o.uint32(42).fork()).ldelim(),
            null != e.memo && Object.hasOwnProperty.call(e, 'memo') && o.uint32(50).string(e.memo),
            null != e.adminKey &&
              Object.hasOwnProperty.call(e, 'adminKey') &&
              $root.proto.Key.encode(e.adminKey, o.uint32(58).fork()).ldelim(),
            null != e.signers &&
              Object.hasOwnProperty.call(e, 'signers') &&
              $root.proto.KeyList.encode(e.signers, o.uint32(66).fork()).ldelim(),
            null != e.creatorAccountID &&
              Object.hasOwnProperty.call(e, 'creatorAccountID') &&
              $root.proto.AccountID.encode(e.creatorAccountID, o.uint32(74).fork()).ldelim(),
            null != e.payerAccountID &&
              Object.hasOwnProperty.call(e, 'payerAccountID') &&
              $root.proto.AccountID.encode(e.payerAccountID, o.uint32(82).fork()).ldelim(),
            null != e.scheduledTransactionID &&
              Object.hasOwnProperty.call(e, 'scheduledTransactionID') &&
              $root.proto.TransactionID.encode(e.scheduledTransactionID, o.uint32(90).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ScheduleInfo(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.scheduleID = $root.proto.ScheduleID.decode(e, e.uint32());
                break;
              case 2:
                i.deletionTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 3:
                i.executionTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 4:
                i.expirationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 5:
                i.scheduledTransactionBody = $root.proto.SchedulableTransactionBody.decode(e, e.uint32());
                break;
              case 6:
                i.memo = e.string();
                break;
              case 7:
                i.adminKey = $root.proto.Key.decode(e, e.uint32());
                break;
              case 8:
                i.signers = $root.proto.KeyList.decode(e, e.uint32());
                break;
              case 9:
                i.creatorAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 10:
                i.payerAccountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 11:
                i.scheduledTransactionID = $root.proto.TransactionID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ScheduleGetInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.scheduleInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.scheduleInfo &&
              Object.hasOwnProperty.call(e, 'scheduleInfo') &&
              $root.proto.ScheduleInfo.encode(e.scheduleInfo, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ScheduleGetInfoResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.scheduleInfo = $root.proto.ScheduleInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetAccountNftInfosQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.accountID = null),
        (e.prototype.start = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.end = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.start && Object.hasOwnProperty.call(e, 'start') && o.uint32(24).int64(e.start),
            null != e.end && Object.hasOwnProperty.call(e, 'end') && o.uint32(32).int64(e.end),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetAccountNftInfosQuery(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.start = e.int64();
                break;
              case 4:
                i.end = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetAccountNftInfosResponse: (function () {
      function e(e) {
        if (((this.nfts = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.nfts = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.nfts && e.nfts.length)
          )
            for (var t = 0; t < e.nfts.length; ++t)
              $root.proto.TokenNftInfo.encode(e.nfts[t], o.uint32(18).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (
            var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetAccountNftInfosResponse(), d;
            e.pos < n;

          )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                (i.nfts && i.nfts.length) || (i.nfts = []), i.nfts.push($root.proto.TokenNftInfo.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    NftID: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.tokenID = null),
        (e.prototype.serialNumber = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.tokenID &&
              Object.hasOwnProperty.call(e, 'tokenID') &&
              $root.proto.TokenID.encode(e.tokenID, o.uint32(10).fork()).ldelim(),
            null != e.serialNumber &&
              Object.hasOwnProperty.call(e, 'serialNumber') &&
              o.uint32(16).int64(e.serialNumber),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.NftID(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.tokenID = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 2:
                i.serialNumber = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetNftInfoQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.nftID = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.nftID &&
              Object.hasOwnProperty.call(e, 'nftID') &&
              $root.proto.NftID.encode(e.nftID, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetNftInfoQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.nftID = $root.proto.NftID.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenNftInfo: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.nftID = null),
        (e.prototype.accountID = null),
        (e.prototype.creationTime = null),
        (e.prototype.metadata = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.nftID &&
              Object.hasOwnProperty.call(e, 'nftID') &&
              $root.proto.NftID.encode(e.nftID, o.uint32(10).fork()).ldelim(),
            null != e.accountID &&
              Object.hasOwnProperty.call(e, 'accountID') &&
              $root.proto.AccountID.encode(e.accountID, o.uint32(18).fork()).ldelim(),
            null != e.creationTime &&
              Object.hasOwnProperty.call(e, 'creationTime') &&
              $root.proto.Timestamp.encode(e.creationTime, o.uint32(26).fork()).ldelim(),
            null != e.metadata && Object.hasOwnProperty.call(e, 'metadata') && o.uint32(34).bytes(e.metadata),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenNftInfo(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.nftID = $root.proto.NftID.decode(e, e.uint32());
                break;
              case 2:
                i.accountID = $root.proto.AccountID.decode(e, e.uint32());
                break;
              case 3:
                i.creationTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 4:
                i.metadata = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetNftInfoResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.nft = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.nft &&
              Object.hasOwnProperty.call(e, 'nft') &&
              $root.proto.TokenNftInfo.encode(e.nft, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetNftInfoResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.nft = $root.proto.TokenNftInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetNftInfosQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.tokenID = null),
        (e.prototype.start = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.prototype.end = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.QueryHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.tokenID &&
              Object.hasOwnProperty.call(e, 'tokenID') &&
              $root.proto.TokenID.encode(e.tokenID, o.uint32(18).fork()).ldelim(),
            null != e.start && Object.hasOwnProperty.call(e, 'start') && o.uint32(24).int64(e.start),
            null != e.end && Object.hasOwnProperty.call(e, 'end') && o.uint32(32).int64(e.end),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetNftInfosQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.QueryHeader.decode(e, e.uint32());
                break;
              case 2:
                i.tokenID = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 3:
                i.start = e.int64();
                break;
              case 4:
                i.end = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenGetNftInfosResponse: (function () {
      function e(e) {
        if (((this.nfts = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.header = null),
        (e.prototype.tokenID = null),
        (e.prototype.nfts = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.header &&
              Object.hasOwnProperty.call(e, 'header') &&
              $root.proto.ResponseHeader.encode(e.header, o.uint32(10).fork()).ldelim(),
            null != e.tokenID &&
              Object.hasOwnProperty.call(e, 'tokenID') &&
              $root.proto.TokenID.encode(e.tokenID, o.uint32(18).fork()).ldelim(),
            null != e.nfts && e.nfts.length)
          )
            for (var t = 0; t < e.nfts.length; ++t)
              $root.proto.TokenNftInfo.encode(e.nfts[t], o.uint32(26).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TokenGetNftInfosResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.header = $root.proto.ResponseHeader.decode(e, e.uint32());
                break;
              case 2:
                i.tokenID = $root.proto.TokenID.decode(e, e.uint32());
                break;
              case 3:
                (i.nfts && i.nfts.length) || (i.nfts = []), i.nfts.push($root.proto.TokenNftInfo.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Response: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      (e.prototype.getByKey = null),
        (e.prototype.getBySolidityID = null),
        (e.prototype.contractCallLocal = null),
        (e.prototype.contractGetBytecodeResponse = null),
        (e.prototype.contractGetInfo = null),
        (e.prototype.contractGetRecordsResponse = null),
        (e.prototype.cryptogetAccountBalance = null),
        (e.prototype.cryptoGetAccountRecords = null),
        (e.prototype.cryptoGetInfo = null),
        (e.prototype.cryptoGetLiveHash = null),
        (e.prototype.cryptoGetProxyStakers = null),
        (e.prototype.fileGetContents = null),
        (e.prototype.fileGetInfo = null),
        (e.prototype.transactionGetReceipt = null),
        (e.prototype.transactionGetRecord = null),
        (e.prototype.transactionGetFastRecord = null),
        (e.prototype.consensusGetTopicInfo = null),
        (e.prototype.networkGetVersionInfo = null),
        (e.prototype.tokenGetInfo = null),
        (e.prototype.scheduleGetInfo = null),
        (e.prototype.tokenGetAccountNftInfos = null),
        (e.prototype.tokenGetNftInfo = null),
        (e.prototype.tokenGetNftInfos = null),
        (e.prototype.networkGetExecutionTime = null);
      let o;
      return (
        Object.defineProperty(e.prototype, 'response', {
          get: $util.oneOfGetter(
            (o = [
              'getByKey',
              'getBySolidityID',
              'contractCallLocal',
              'contractGetBytecodeResponse',
              'contractGetInfo',
              'contractGetRecordsResponse',
              'cryptogetAccountBalance',
              'cryptoGetAccountRecords',
              'cryptoGetInfo',
              'cryptoGetLiveHash',
              'cryptoGetProxyStakers',
              'fileGetContents',
              'fileGetInfo',
              'transactionGetReceipt',
              'transactionGetRecord',
              'transactionGetFastRecord',
              'consensusGetTopicInfo',
              'networkGetVersionInfo',
              'tokenGetInfo',
              'scheduleGetInfo',
              'tokenGetAccountNftInfos',
              'tokenGetNftInfo',
              'tokenGetNftInfos',
              'networkGetExecutionTime',
            ])
          ),
          set: $util.oneOfSetter(o),
        }),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.getByKey &&
              Object.hasOwnProperty.call(e, 'getByKey') &&
              $root.proto.GetByKeyResponse.encode(e.getByKey, o.uint32(10).fork()).ldelim(),
            null != e.getBySolidityID &&
              Object.hasOwnProperty.call(e, 'getBySolidityID') &&
              $root.proto.GetBySolidityIDResponse.encode(e.getBySolidityID, o.uint32(18).fork()).ldelim(),
            null != e.contractCallLocal &&
              Object.hasOwnProperty.call(e, 'contractCallLocal') &&
              $root.proto.ContractCallLocalResponse.encode(e.contractCallLocal, o.uint32(26).fork()).ldelim(),
            null != e.contractGetInfo &&
              Object.hasOwnProperty.call(e, 'contractGetInfo') &&
              $root.proto.ContractGetInfoResponse.encode(e.contractGetInfo, o.uint32(34).fork()).ldelim(),
            null != e.contractGetBytecodeResponse &&
              Object.hasOwnProperty.call(e, 'contractGetBytecodeResponse') &&
              $root.proto.ContractGetBytecodeResponse.encode(
                e.contractGetBytecodeResponse,
                o.uint32(42).fork()
              ).ldelim(),
            null != e.contractGetRecordsResponse &&
              Object.hasOwnProperty.call(e, 'contractGetRecordsResponse') &&
              $root.proto.ContractGetRecordsResponse.encode(e.contractGetRecordsResponse, o.uint32(50).fork()).ldelim(),
            null != e.cryptogetAccountBalance &&
              Object.hasOwnProperty.call(e, 'cryptogetAccountBalance') &&
              $root.proto.CryptoGetAccountBalanceResponse.encode(
                e.cryptogetAccountBalance,
                o.uint32(58).fork()
              ).ldelim(),
            null != e.cryptoGetAccountRecords &&
              Object.hasOwnProperty.call(e, 'cryptoGetAccountRecords') &&
              $root.proto.CryptoGetAccountRecordsResponse.encode(
                e.cryptoGetAccountRecords,
                o.uint32(66).fork()
              ).ldelim(),
            null != e.cryptoGetInfo &&
              Object.hasOwnProperty.call(e, 'cryptoGetInfo') &&
              $root.proto.CryptoGetInfoResponse.encode(e.cryptoGetInfo, o.uint32(74).fork()).ldelim(),
            null != e.cryptoGetLiveHash &&
              Object.hasOwnProperty.call(e, 'cryptoGetLiveHash') &&
              $root.proto.CryptoGetLiveHashResponse.encode(e.cryptoGetLiveHash, o.uint32(82).fork()).ldelim(),
            null != e.cryptoGetProxyStakers &&
              Object.hasOwnProperty.call(e, 'cryptoGetProxyStakers') &&
              $root.proto.CryptoGetStakersResponse.encode(e.cryptoGetProxyStakers, o.uint32(90).fork()).ldelim(),
            null != e.fileGetContents &&
              Object.hasOwnProperty.call(e, 'fileGetContents') &&
              $root.proto.FileGetContentsResponse.encode(e.fileGetContents, o.uint32(98).fork()).ldelim(),
            null != e.fileGetInfo &&
              Object.hasOwnProperty.call(e, 'fileGetInfo') &&
              $root.proto.FileGetInfoResponse.encode(e.fileGetInfo, o.uint32(106).fork()).ldelim(),
            null != e.transactionGetReceipt &&
              Object.hasOwnProperty.call(e, 'transactionGetReceipt') &&
              $root.proto.TransactionGetReceiptResponse.encode(e.transactionGetReceipt, o.uint32(114).fork()).ldelim(),
            null != e.transactionGetRecord &&
              Object.hasOwnProperty.call(e, 'transactionGetRecord') &&
              $root.proto.TransactionGetRecordResponse.encode(e.transactionGetRecord, o.uint32(122).fork()).ldelim(),
            null != e.transactionGetFastRecord &&
              Object.hasOwnProperty.call(e, 'transactionGetFastRecord') &&
              $root.proto.TransactionGetFastRecordResponse.encode(
                e.transactionGetFastRecord,
                o.uint32(130).fork()
              ).ldelim(),
            null != e.consensusGetTopicInfo &&
              Object.hasOwnProperty.call(e, 'consensusGetTopicInfo') &&
              $root.proto.ConsensusGetTopicInfoResponse.encode(e.consensusGetTopicInfo, o.uint32(1202).fork()).ldelim(),
            null != e.networkGetVersionInfo &&
              Object.hasOwnProperty.call(e, 'networkGetVersionInfo') &&
              $root.proto.NetworkGetVersionInfoResponse.encode(e.networkGetVersionInfo, o.uint32(1210).fork()).ldelim(),
            null != e.tokenGetInfo &&
              Object.hasOwnProperty.call(e, 'tokenGetInfo') &&
              $root.proto.TokenGetInfoResponse.encode(e.tokenGetInfo, o.uint32(1218).fork()).ldelim(),
            null != e.scheduleGetInfo &&
              Object.hasOwnProperty.call(e, 'scheduleGetInfo') &&
              $root.proto.ScheduleGetInfoResponse.encode(e.scheduleGetInfo, o.uint32(1226).fork()).ldelim(),
            null != e.tokenGetAccountNftInfos &&
              Object.hasOwnProperty.call(e, 'tokenGetAccountNftInfos') &&
              $root.proto.TokenGetAccountNftInfosResponse.encode(
                e.tokenGetAccountNftInfos,
                o.uint32(1234).fork()
              ).ldelim(),
            null != e.tokenGetNftInfo &&
              Object.hasOwnProperty.call(e, 'tokenGetNftInfo') &&
              $root.proto.TokenGetNftInfoResponse.encode(e.tokenGetNftInfo, o.uint32(1242).fork()).ldelim(),
            null != e.tokenGetNftInfos &&
              Object.hasOwnProperty.call(e, 'tokenGetNftInfos') &&
              $root.proto.TokenGetNftInfosResponse.encode(e.tokenGetNftInfos, o.uint32(1250).fork()).ldelim(),
            null != e.networkGetExecutionTime &&
              Object.hasOwnProperty.call(e, 'networkGetExecutionTime') &&
              $root.proto.NetworkGetExecutionTimeResponse.encode(
                e.networkGetExecutionTime,
                o.uint32(1258).fork()
              ).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Response(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.getByKey = $root.proto.GetByKeyResponse.decode(e, e.uint32());
                break;
              case 2:
                i.getBySolidityID = $root.proto.GetBySolidityIDResponse.decode(e, e.uint32());
                break;
              case 3:
                i.contractCallLocal = $root.proto.ContractCallLocalResponse.decode(e, e.uint32());
                break;
              case 5:
                i.contractGetBytecodeResponse = $root.proto.ContractGetBytecodeResponse.decode(e, e.uint32());
                break;
              case 4:
                i.contractGetInfo = $root.proto.ContractGetInfoResponse.decode(e, e.uint32());
                break;
              case 6:
                i.contractGetRecordsResponse = $root.proto.ContractGetRecordsResponse.decode(e, e.uint32());
                break;
              case 7:
                i.cryptogetAccountBalance = $root.proto.CryptoGetAccountBalanceResponse.decode(e, e.uint32());
                break;
              case 8:
                i.cryptoGetAccountRecords = $root.proto.CryptoGetAccountRecordsResponse.decode(e, e.uint32());
                break;
              case 9:
                i.cryptoGetInfo = $root.proto.CryptoGetInfoResponse.decode(e, e.uint32());
                break;
              case 10:
                i.cryptoGetLiveHash = $root.proto.CryptoGetLiveHashResponse.decode(e, e.uint32());
                break;
              case 11:
                i.cryptoGetProxyStakers = $root.proto.CryptoGetStakersResponse.decode(e, e.uint32());
                break;
              case 12:
                i.fileGetContents = $root.proto.FileGetContentsResponse.decode(e, e.uint32());
                break;
              case 13:
                i.fileGetInfo = $root.proto.FileGetInfoResponse.decode(e, e.uint32());
                break;
              case 14:
                i.transactionGetReceipt = $root.proto.TransactionGetReceiptResponse.decode(e, e.uint32());
                break;
              case 15:
                i.transactionGetRecord = $root.proto.TransactionGetRecordResponse.decode(e, e.uint32());
                break;
              case 16:
                i.transactionGetFastRecord = $root.proto.TransactionGetFastRecordResponse.decode(e, e.uint32());
                break;
              case 150:
                i.consensusGetTopicInfo = $root.proto.ConsensusGetTopicInfoResponse.decode(e, e.uint32());
                break;
              case 151:
                i.networkGetVersionInfo = $root.proto.NetworkGetVersionInfoResponse.decode(e, e.uint32());
                break;
              case 152:
                i.tokenGetInfo = $root.proto.TokenGetInfoResponse.decode(e, e.uint32());
                break;
              case 153:
                i.scheduleGetInfo = $root.proto.ScheduleGetInfoResponse.decode(e, e.uint32());
                break;
              case 154:
                i.tokenGetAccountNftInfos = $root.proto.TokenGetAccountNftInfosResponse.decode(e, e.uint32());
                break;
              case 155:
                i.tokenGetNftInfo = $root.proto.TokenGetNftInfoResponse.decode(e, e.uint32());
                break;
              case 156:
                i.tokenGetNftInfos = $root.proto.TokenGetNftInfosResponse.decode(e, e.uint32());
                break;
              case 157:
                i.networkGetExecutionTime = $root.proto.NetworkGetExecutionTimeResponse.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    CryptoService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.createAccount = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'createAccount'}
        ),
        Object.defineProperty(
          (e.prototype.updateAccount = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'updateAccount'}
        ),
        Object.defineProperty(
          (e.prototype.cryptoTransfer = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'cryptoTransfer'}
        ),
        Object.defineProperty(
          (e.prototype.cryptoDelete = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'cryptoDelete'}
        ),
        Object.defineProperty(
          (e.prototype.addLiveHash = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'addLiveHash'}
        ),
        Object.defineProperty(
          (e.prototype.deleteLiveHash = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'deleteLiveHash'}
        ),
        Object.defineProperty(
          (e.prototype.getLiveHash = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getLiveHash'}
        ),
        Object.defineProperty(
          (e.prototype.getAccountRecords = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getAccountRecords'}
        ),
        Object.defineProperty(
          (e.prototype.cryptoGetBalance = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'cryptoGetBalance'}
        ),
        Object.defineProperty(
          (e.prototype.getAccountInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getAccountInfo'}
        ),
        Object.defineProperty(
          (e.prototype.getTransactionReceipts = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getTransactionReceipts'}
        ),
        Object.defineProperty(
          (e.prototype.getFastTransactionRecord = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getFastTransactionRecord'}
        ),
        Object.defineProperty(
          (e.prototype.getTxRecordByTxID = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getTxRecordByTxID'}
        ),
        Object.defineProperty(
          (e.prototype.getStakersByAccountID = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getStakersByAccountID'}
        ),
        e
      );
    })(),
    FileService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.createFile = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'createFile'}
        ),
        Object.defineProperty(
          (e.prototype.updateFile = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'updateFile'}
        ),
        Object.defineProperty(
          (e.prototype.deleteFile = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'deleteFile'}
        ),
        Object.defineProperty(
          (e.prototype.appendContent = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'appendContent'}
        ),
        Object.defineProperty(
          (e.prototype.getFileContent = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getFileContent'}
        ),
        Object.defineProperty(
          (e.prototype.getFileInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getFileInfo'}
        ),
        Object.defineProperty(
          (e.prototype.systemDelete = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'systemDelete'}
        ),
        Object.defineProperty(
          (e.prototype.systemUndelete = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'systemUndelete'}
        ),
        e
      );
    })(),
    FreezeService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.freeze = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'freeze'}
        ),
        e
      );
    })(),
    ConsensusTopicQuery: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.topicID = null),
        (e.prototype.consensusStartTime = null),
        (e.prototype.consensusEndTime = null),
        (e.prototype.limit = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.topicID &&
              Object.hasOwnProperty.call(e, 'topicID') &&
              $root.proto.TopicID.encode(e.topicID, o.uint32(10).fork()).ldelim(),
            null != e.consensusStartTime &&
              Object.hasOwnProperty.call(e, 'consensusStartTime') &&
              $root.proto.Timestamp.encode(e.consensusStartTime, o.uint32(18).fork()).ldelim(),
            null != e.consensusEndTime &&
              Object.hasOwnProperty.call(e, 'consensusEndTime') &&
              $root.proto.Timestamp.encode(e.consensusEndTime, o.uint32(26).fork()).ldelim(),
            null != e.limit && Object.hasOwnProperty.call(e, 'limit') && o.uint32(32).uint64(e.limit),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusTopicQuery(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.topicID = $root.proto.TopicID.decode(e, e.uint32());
                break;
              case 2:
                i.consensusStartTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 3:
                i.consensusEndTime = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 4:
                i.limit = e.uint64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ConsensusTopicResponse: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.consensusTimestamp = null),
        (e.prototype.message = $util.newBuffer([])),
        (e.prototype.runningHash = $util.newBuffer([])),
        (e.prototype.sequenceNumber = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.runningHashVersion = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.chunkInfo = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.consensusTimestamp &&
              Object.hasOwnProperty.call(e, 'consensusTimestamp') &&
              $root.proto.Timestamp.encode(e.consensusTimestamp, o.uint32(10).fork()).ldelim(),
            null != e.message && Object.hasOwnProperty.call(e, 'message') && o.uint32(18).bytes(e.message),
            null != e.runningHash && Object.hasOwnProperty.call(e, 'runningHash') && o.uint32(26).bytes(e.runningHash),
            null != e.sequenceNumber &&
              Object.hasOwnProperty.call(e, 'sequenceNumber') &&
              o.uint32(32).uint64(e.sequenceNumber),
            null != e.runningHashVersion &&
              Object.hasOwnProperty.call(e, 'runningHashVersion') &&
              o.uint32(40).uint64(e.runningHashVersion),
            null != e.chunkInfo &&
              Object.hasOwnProperty.call(e, 'chunkInfo') &&
              $root.proto.ConsensusMessageChunkInfo.encode(e.chunkInfo, o.uint32(50).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ConsensusTopicResponse(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.consensusTimestamp = $root.proto.Timestamp.decode(e, e.uint32());
                break;
              case 2:
                i.message = e.bytes();
                break;
              case 3:
                i.runningHash = e.bytes();
                break;
              case 4:
                i.sequenceNumber = e.uint64();
                break;
              case 5:
                i.runningHashVersion = e.uint64();
                break;
              case 6:
                i.chunkInfo = $root.proto.ConsensusMessageChunkInfo.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    MirrorConsensusService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.subscribeTopic = function e(o, t) {
            return this.rpcCall(e, $root.proto.ConsensusTopicQuery, $root.proto.ConsensusTopicResponse, o, t);
          }),
          'name',
          {value: 'subscribeTopic'}
        ),
        e
      );
    })(),
    NetworkService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.getVersionInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getVersionInfo'}
        ),
        Object.defineProperty(
          (e.prototype.getExecutionTime = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getExecutionTime'}
        ),
        Object.defineProperty(
          (e.prototype.uncheckedSubmit = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'uncheckedSubmit'}
        ),
        e
      );
    })(),
    ScheduleService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.createSchedule = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'createSchedule'}
        ),
        Object.defineProperty(
          (e.prototype.signSchedule = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'signSchedule'}
        ),
        Object.defineProperty(
          (e.prototype.deleteSchedule = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'deleteSchedule'}
        ),
        Object.defineProperty(
          (e.prototype.getScheduleInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getScheduleInfo'}
        ),
        e
      );
    })(),
    SmartContractService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.createContract = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'createContract'}
        ),
        Object.defineProperty(
          (e.prototype.updateContract = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'updateContract'}
        ),
        Object.defineProperty(
          (e.prototype.contractCallMethod = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'contractCallMethod'}
        ),
        Object.defineProperty(
          (e.prototype.getContractInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getContractInfo'}
        ),
        Object.defineProperty(
          (e.prototype.contractCallLocalMethod = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'contractCallLocalMethod'}
        ),
        Object.defineProperty(
          (e.prototype.contractGetBytecode = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'ContractGetBytecode'}
        ),
        Object.defineProperty(
          (e.prototype.getBySolidityID = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getBySolidityID'}
        ),
        Object.defineProperty(
          (e.prototype.getTxRecordByContractID = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getTxRecordByContractID'}
        ),
        Object.defineProperty(
          (e.prototype.deleteContract = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'deleteContract'}
        ),
        Object.defineProperty(
          (e.prototype.systemDelete = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'systemDelete'}
        ),
        Object.defineProperty(
          (e.prototype.systemUndelete = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'systemUndelete'}
        ),
        e
      );
    })(),
    ThrottleGroup: (function () {
      function e(e) {
        if (((this.operations = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.operations = $util.emptyArray),
        (e.prototype.milliOpsPerSec = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.operations && e.operations.length)) {
            o.uint32(10).fork();
            for (var t = 0; t < e.operations.length; ++t) o.int32(e.operations[t]);
            o.ldelim();
          }
          return (
            null != e.milliOpsPerSec &&
              Object.hasOwnProperty.call(e, 'milliOpsPerSec') &&
              o.uint32(16).uint64(e.milliOpsPerSec),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ThrottleGroup(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                if (((i.operations && i.operations.length) || (i.operations = []), 2 == (7 & d)))
                  for (var a = e.uint32() + e.pos; e.pos < a; ) i.operations.push(e.int32());
                else i.operations.push(e.int32());
                break;
              case 2:
                i.milliOpsPerSec = e.uint64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ThrottleBucket: (function () {
      function e(e) {
        if (((this.throttleGroups = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.name = ''),
        (e.prototype.burstPeriodMs = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.prototype.throttleGroups = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if (
            (o || (o = $Writer.create()),
            null != e.name && Object.hasOwnProperty.call(e, 'name') && o.uint32(10).string(e.name),
            null != e.burstPeriodMs &&
              Object.hasOwnProperty.call(e, 'burstPeriodMs') &&
              o.uint32(16).uint64(e.burstPeriodMs),
            null != e.throttleGroups && e.throttleGroups.length)
          )
            for (var t = 0; t < e.throttleGroups.length; ++t)
              $root.proto.ThrottleGroup.encode(e.throttleGroups[t], o.uint32(26).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ThrottleBucket(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.name = e.string();
                break;
              case 2:
                i.burstPeriodMs = e.uint64();
                break;
              case 3:
                (i.throttleGroups && i.throttleGroups.length) || (i.throttleGroups = []),
                  i.throttleGroups.push($root.proto.ThrottleGroup.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    ThrottleDefinitions: (function () {
      function e(e) {
        if (((this.throttleBuckets = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.throttleBuckets = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.throttleBuckets && e.throttleBuckets.length))
            for (var t = 0; t < e.throttleBuckets.length; ++t)
              $root.proto.ThrottleBucket.encode(e.throttleBuckets[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.ThrottleDefinitions(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.throttleBuckets && i.throttleBuckets.length) || (i.throttleBuckets = []),
                  i.throttleBuckets.push($root.proto.ThrottleBucket.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TokenService: (function () {
      function e(e, o, t) {
        $protobuf.rpc.Service.call(this, e, o, t);
      }
      return (
        ((e.prototype = Object.create($protobuf.rpc.Service.prototype)).constructor = e),
        (e.create = function (e, o, t) {
          return new this(e, o, t);
        }),
        Object.defineProperty(
          (e.prototype.createToken = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'createToken'}
        ),
        Object.defineProperty(
          (e.prototype.updateToken = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'updateToken'}
        ),
        Object.defineProperty(
          (e.prototype.mintToken = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'mintToken'}
        ),
        Object.defineProperty(
          (e.prototype.burnToken = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'burnToken'}
        ),
        Object.defineProperty(
          (e.prototype.deleteToken = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'deleteToken'}
        ),
        Object.defineProperty(
          (e.prototype.wipeTokenAccount = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'wipeTokenAccount'}
        ),
        Object.defineProperty(
          (e.prototype.freezeTokenAccount = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'freezeTokenAccount'}
        ),
        Object.defineProperty(
          (e.prototype.unfreezeTokenAccount = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'unfreezeTokenAccount'}
        ),
        Object.defineProperty(
          (e.prototype.grantKycToTokenAccount = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'grantKycToTokenAccount'}
        ),
        Object.defineProperty(
          (e.prototype.revokeKycFromTokenAccount = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'revokeKycFromTokenAccount'}
        ),
        Object.defineProperty(
          (e.prototype.associateTokens = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'associateTokens'}
        ),
        Object.defineProperty(
          (e.prototype.dissociateTokens = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'dissociateTokens'}
        ),
        Object.defineProperty(
          (e.prototype.updateTokenFeeSchedule = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'updateTokenFeeSchedule'}
        ),
        Object.defineProperty(
          (e.prototype.getTokenInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getTokenInfo'}
        ),
        Object.defineProperty(
          (e.prototype.getAccountNftInfos = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getAccountNftInfos'}
        ),
        Object.defineProperty(
          (e.prototype.getTokenNftInfo = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getTokenNftInfo'}
        ),
        Object.defineProperty(
          (e.prototype.getTokenNftInfos = function e(o, t) {
            return this.rpcCall(e, $root.proto.Query, $root.proto.Response, o, t);
          }),
          'name',
          {value: 'getTokenNftInfos'}
        ),
        Object.defineProperty(
          (e.prototype.pauseToken = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'pauseToken'}
        ),
        Object.defineProperty(
          (e.prototype.unpauseToken = function e(o, t) {
            return this.rpcCall(e, $root.proto.Transaction, $root.proto.TransactionResponse, o, t);
          }),
          'name',
          {value: 'unpauseToken'}
        ),
        e
      );
    })(),
    SignedTransaction: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.bodyBytes = $util.newBuffer([])),
        (e.prototype.sigMap = null),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.bodyBytes && Object.hasOwnProperty.call(e, 'bodyBytes') && o.uint32(10).bytes(e.bodyBytes),
            null != e.sigMap &&
              Object.hasOwnProperty.call(e, 'sigMap') &&
              $root.proto.SignatureMap.encode(e.sigMap, o.uint32(18).fork()).ldelim(),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.SignedTransaction(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.bodyBytes = e.bytes();
                break;
              case 2:
                i.sigMap = $root.proto.SignatureMap.decode(e, e.uint32());
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    TransactionList: (function () {
      function e(e) {
        if (((this.transactionList = []), e))
          for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.transactionList = $util.emptyArray),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          if ((o || (o = $Writer.create()), null != e.transactionList && e.transactionList.length))
            for (var t = 0; t < e.transactionList.length; ++t)
              $root.proto.Transaction.encode(e.transactionList[t], o.uint32(10).fork()).ldelim();
          return o;
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.TransactionList(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                (i.transactionList && i.transactionList.length) || (i.transactionList = []),
                  i.transactionList.push($root.proto.Transaction.decode(e, e.uint32()));
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    DoubleValue: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(9).double(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.DoubleValue(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.double();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    FloatValue: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(13).float(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.FloatValue(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.float();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Int64Value: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).int64(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Int64Value(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.int64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    UInt64Value: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).uint64(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.UInt64Value(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.uint64();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    Int32Value: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).int32(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.Int32Value(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.int32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    UInt32Value: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = 0),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).uint32(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.UInt32Value(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.uint32();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    BoolValue: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = !1),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).bool(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.BoolValue(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.bool();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    StringValue: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = ''),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(10).string(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.StringValue(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.string();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
    BytesValue: (function () {
      function e(e) {
        if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
      }
      return (
        (e.prototype.value = $util.newBuffer([])),
        (e.create = function (o) {
          return new e(o);
        }),
        (e.encode = function (e, o) {
          return (
            o || (o = $Writer.create()),
            null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(10).bytes(e.value),
            o
          );
        }),
        (e.decode = function (e, o) {
          e instanceof $Reader || (e = $Reader.create(e));
          for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.proto.BytesValue(), d; e.pos < n; )
            switch (((d = e.uint32()), d >>> 3)) {
              case 1:
                i.value = e.bytes();
                break;
              default:
                e.skipType(7 & d);
            }
          return i;
        }),
        e
      );
    })(),
  };
  return e;
})());
exports.proto = proto;
const google = ($root.google = (() => {
  const e = {
    protobuf: (function () {
      const e = {
        DoubleValue: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = 0),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(9).double(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.DoubleValue(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.double();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        FloatValue: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = 0),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(13).float(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.FloatValue(), d; e.pos < n; )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.float();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        Int64Value: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = $util.Long ? $util.Long.fromBits(0, 0, !1) : 0),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).int64(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.Int64Value(), d; e.pos < n; )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.int64();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        UInt64Value: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = $util.Long ? $util.Long.fromBits(0, 0, !0) : 0),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).uint64(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.UInt64Value(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.uint64();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        Int32Value: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = 0),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).int32(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.Int32Value(), d; e.pos < n; )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.int32();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        UInt32Value: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = 0),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).uint32(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.UInt32Value(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.uint32();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        BoolValue: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = !1),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(8).bool(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.BoolValue(), d; e.pos < n; )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.bool();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        StringValue: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = ''),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(10).string(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (
                var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.StringValue(), d;
                e.pos < n;

              )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.string();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
        BytesValue: (function () {
          function e(e) {
            if (e) for (var o = Object.keys(e), t = 0; t < o.length; ++t) null != e[o[t]] && (this[o[t]] = e[o[t]]);
          }
          return (
            (e.prototype.value = $util.newBuffer([])),
            (e.create = function (o) {
              return new e(o);
            }),
            (e.encode = function (e, o) {
              return (
                o || (o = $Writer.create()),
                null != e.value && Object.hasOwnProperty.call(e, 'value') && o.uint32(10).bytes(e.value),
                o
              );
            }),
            (e.decode = function (e, o) {
              e instanceof $Reader || (e = $Reader.create(e));
              for (var n = void 0 === o ? e.len : e.pos + o, i = new $root.google.protobuf.BytesValue(), d; e.pos < n; )
                switch (((d = e.uint32()), d >>> 3)) {
                  case 1:
                    i.value = e.bytes();
                    break;
                  default:
                    e.skipType(7 & d);
                }
              return i;
            }),
            e
          );
        })(),
      };
      return e;
    })(),
  };
  return e;
})());
exports.google = google;
