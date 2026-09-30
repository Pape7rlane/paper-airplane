extern crate bytes;
#[macro_use]
extern crate log;

mod error;
mod kcp;

/// The `KCP` prelude
pub mod prelude {
    pub use super::{KCP_OVERHEAD, Kcp, get_conv};
}

pub use error::Error;
pub use kcp::{KCP_OVERHEAD, Kcp, get_conv, get_sn, set_conv};

/// KCP result
pub type KcpResult<T> = Result<T, Error>;
