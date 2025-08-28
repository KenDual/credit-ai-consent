// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

contract ConsentRegistry {
    event ConsentGranted(address indexed user, string dataType, uint256 expiry);
    event ConsentRevoked(address indexed user, string dataType);

    struct Consent {
        bool granted;
        uint256 expiry;      // epoch seconds; 0 = never expires
        string termsHash;    // IPFS/SHA256 hash of the consent doc
    }

    mapping(address => mapping(string => Consent)) private _consents;

    function grant(string calldata dataType, uint256 expiry, string calldata termsHash) external {
        _consents[msg.sender][dataType] = Consent(true, expiry, termsHash);
        emit ConsentGranted(msg.sender, dataType, expiry);
    }

    function revoke(string calldata dataType) external {
        delete _consents[msg.sender][dataType];
        emit ConsentRevoked(msg.sender, dataType);
    }

    function check(address user, string calldata dataType)
        external
        view
        returns (bool granted, uint256 expiry, string memory termsHash)
    {
        Consent memory c = _consents[user][dataType];
        bool valid = c.granted && (c.expiry == 0 || block.timestamp <= c.expiry);
        return (valid, c.expiry, c.termsHash);
    }
}
