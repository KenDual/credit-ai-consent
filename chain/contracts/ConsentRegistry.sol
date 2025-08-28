pragma solidity ^0.8.20;

contract ConsentRegistry {
    event ConsentGiven(bytes32 indexed consentId, address indexed subject, string scopes, uint256 expiry, string dataHash);
    event ConsentRevoked(bytes32 indexed consentId, address indexed subject);

    mapping(bytes32 => bool) public active;

    function give(string calldata scopes, uint256 expiry, string calldata dataHash) external returns (bytes32) {
        bytes32 cid = keccak256(abi.encodePacked(msg.sender, scopes, expiry, dataHash, block.timestamp));
        active[cid] = true;
        emit ConsentGiven(cid, msg.sender, scopes, expiry, dataHash);
        return cid;
    }

    function revoke(bytes32 consentId) external {
        require(active[consentId], "not active");
        active[consentId] = false;
        emit ConsentRevoked(consentId, msg.sender);
    }
}
